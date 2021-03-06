package com.firefly.codec.http2.hpack;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import com.firefly.codec.http2.hpack.HpackContext.Entry;
import com.firefly.codec.http2.hpack.HpackContext.StaticEntry;
import com.firefly.codec.http2.model.HttpField;
import com.firefly.codec.http2.model.HttpHeader;
import com.firefly.codec.http2.model.HttpScheme;
import com.firefly.codec.http2.model.HttpStatus;
import com.firefly.codec.http2.model.HttpVersion;
import com.firefly.codec.http2.model.MetaData;
import com.firefly.codec.http2.model.PreEncodedHttpField;
import com.firefly.utils.lang.TypeUtils;
import com.firefly.utils.log.Log;
import com.firefly.utils.log.LogFactory;

public class HpackEncoder {
	
	private static Log log = LogFactory.getInstance().getLog("firefly-system");
	
	private final static HttpField[] status = new HttpField[599];

	final static EnumSet<HttpHeader> DO_NOT_HUFFMAN = EnumSet.of(
			HttpHeader.AUTHORIZATION, 
			HttpHeader.CONTENT_MD5,
			HttpHeader.PROXY_AUTHENTICATE, 
			HttpHeader.PROXY_AUTHORIZATION);

	final static EnumSet<HttpHeader> DO_NOT_INDEX = EnumSet.of(
			// HttpHeader.C_PATH, // TODO more data needed
			// HttpHeader.DATE, // TODO more data needed
			HttpHeader.AUTHORIZATION, 
			HttpHeader.CONTENT_MD5, 
			HttpHeader.CONTENT_RANGE, 
			HttpHeader.ETAG,
			HttpHeader.IF_MODIFIED_SINCE, 
			HttpHeader.IF_UNMODIFIED_SINCE, 
			HttpHeader.IF_NONE_MATCH, 
			HttpHeader.IF_RANGE,
			HttpHeader.IF_MATCH, 
			HttpHeader.LOCATION, 
			HttpHeader.RANGE, 
			HttpHeader.RETRY_AFTER,
			// HttpHeader.EXPIRES,
			HttpHeader.LAST_MODIFIED, 
			HttpHeader.SET_COOKIE, 
			HttpHeader.SET_COOKIE2);

	final static EnumSet<HttpHeader> NEVER_INDEX = EnumSet.of(
			HttpHeader.AUTHORIZATION,
			HttpHeader.SET_COOKIE,
			HttpHeader.SET_COOKIE2);
	
	static {
        for (HttpStatus.Code code : HttpStatus.Code.values())
            status[code.getCode()] = new PreEncodedHttpField(HttpHeader.C_STATUS,Integer.toString(code.getCode()));
    }
	
	private final HpackContext context;
    private int remoteMaxDynamicTableSize;
    private int localMaxDynamicTableSize;
    
    public HpackEncoder() {
        this(4096,4096);
    }

    public HpackEncoder(int localMaxDynamicTableSize) {
        this(localMaxDynamicTableSize,4096);
    }

	public HpackEncoder(int remoteMaxDynamicTableSize, int localMaxDynamicTableSize) {
		this.remoteMaxDynamicTableSize = remoteMaxDynamicTableSize;
		this.localMaxDynamicTableSize = localMaxDynamicTableSize;
		context = new HpackContext(remoteMaxDynamicTableSize);
	}

	public int getRemoteMaxDynamicTableSize() {
		return remoteMaxDynamicTableSize;
	}

	public void setRemoteMaxDynamicTableSize(int remoteMaxDynamicTableSize) {
		this.remoteMaxDynamicTableSize = remoteMaxDynamicTableSize;
	}

	public int getLocalMaxDynamicTableSize() {
		return localMaxDynamicTableSize;
	}

	public void setLocalMaxDynamicTableSize(int localMaxDynamicTableSize) {
		this.localMaxDynamicTableSize = localMaxDynamicTableSize;
	}

	public HpackContext getHpackContext() {
		return context;
	}
	
	public void encode(ByteBuffer buffer,  MetaData metadata) {
		if (log.isDebugEnabled())
            log.debug(String.format("CtxTbl[%x] encoding", context.hashCode()));

        int pos = buffer.position();

        // Check the dynamic table sizes!
        int maxDynamicTableSize = Math.min(remoteMaxDynamicTableSize,localMaxDynamicTableSize);
        if (maxDynamicTableSize != context.getMaxDynamicTableSize())
            encodeMaxDynamicTableSize(buffer, maxDynamicTableSize);
        
        // Add Request/response meta fields
        if (metadata.isRequest()) {
            MetaData.Request request = (MetaData.Request)metadata;

            // TODO optimise these to avoid HttpField creation
            String scheme=request.getURI().getScheme();
            encode(buffer,new HttpField(HttpHeader.C_SCHEME, scheme == null ? HttpScheme.HTTP.asString() : scheme));
            encode(buffer,new HttpField(HttpHeader.C_METHOD, request.getMethod()));
            encode(buffer,new HttpField(HttpHeader.C_AUTHORITY, request.getURI().getAuthority()));
            encode(buffer,new HttpField(HttpHeader.C_PATH, request.getURI().getPathQuery()));

        } else if (metadata.isResponse()) {
            MetaData.Response response = (MetaData.Response)metadata;
            int code = response.getStatus();
            HttpField s = code < status.length ? status[code] : null;
            if (s == null)
                s = new HttpField.IntValueHttpField(HttpHeader.C_STATUS,code);
            encode(buffer, s);
        }
        
        // Add all the other fields
        for (HttpField field : metadata)
            encode(buffer, field);

        if (log.isDebugEnabled())
            log.debug(String.format("CtxTbl[%x] encoded %d octets", context.hashCode(), buffer.position() - pos));
	}
	
	public void encodeMaxDynamicTableSize(ByteBuffer buffer, int maxDynamicTableSize) {
        if (maxDynamicTableSize > remoteMaxDynamicTableSize)
            throw new IllegalArgumentException();
        buffer.put((byte)0x20);
        NBitInteger.encode(buffer, 5, maxDynamicTableSize);
        context.resize(maxDynamicTableSize);
    }
	
    public void encode(ByteBuffer buffer, HttpField field) {
        final int p = log.isDebugEnabled() ? buffer.position() : -1;

        String encoding = null;

        // Is there an entry for the field?
        Entry entry = context.get(field);
        if (entry != null) {
            // Known field entry, so encode it as indexed
            if (entry.isStatic()) {
                buffer.put(((StaticEntry)entry).getEncodedField());
                if (log.isDebugEnabled())
                    encoding = "IdxFieldS1";
            } else {
                int index = context.index(entry);
                buffer.put((byte)0x80);
                NBitInteger.encode(buffer,7,index);
                if (log.isDebugEnabled())
                    encoding= "IdxField" + (entry.isStatic() ? "S" : "") + (1 + NBitInteger.octectsNeeded(7, index));
            }
        } else {
            // Unknown field entry, so we will have to send literally.
            final boolean indexed;

            // But do we know it's name?
            HttpHeader header = field.getHeader();

            // Select encoding strategy
            if (header == null) {
                // Select encoding strategy for unknown header names
                Entry name = context.get(field.getName());

                if (field instanceof PreEncodedHttpField) {
                    int i = buffer.position();
                    ((PreEncodedHttpField)field).putTo(buffer,HttpVersion.HTTP_2);
                    byte b = buffer.get(i);
                    indexed = b<0 || b >= 0x40;
                    if (log.isDebugEnabled())
                        encoding = indexed ? "PreEncodedIdx" : "PreEncoded";
                } else if (name == null) {  // has the custom header name been seen before?
                    // unknown name and value, so let's index this just in case it is
                    // the first time we have seen a custom name or a custom field.
                    // unless the name is changing, this is worthwhile
                    indexed = true;
                    encodeName(buffer, (byte)0x40, 6, field.getName(), null);
                    encodeValue(buffer, true, field.getValue());
                    if (log.isDebugEnabled())
                        encoding = "LitHuffNHuffVIdx";
                } else {
                    // known custom name, but unknown value.
                    // This is probably a custom field with changing value, so don't index.
                    indexed = false;
                    encodeName(buffer, (byte)0x00, 4, field.getName(), null);
                    encodeValue(buffer, true, field.getValue());
                    if (log.isDebugEnabled())
                        encoding = "LitHuffNHuffV!Idx";
                }
            } else {
                // Select encoding strategy for known header names
                Entry name = context.get(header);

                if (field instanceof PreEncodedHttpField) {
                    // Preencoded field
                    int i = buffer.position();
                    ((PreEncodedHttpField)field).putTo(buffer,HttpVersion.HTTP_2);
                    byte b = buffer.get(i);
                    indexed = b < 0 || b >= 0x40;
                    if (log.isDebugEnabled())
                        encoding = indexed?"PreEncodedIdx":"PreEncoded";
                } else if (DO_NOT_INDEX.contains(header)) {
                    // Non indexed field
                    indexed = false;
                    boolean never_index = NEVER_INDEX.contains(header);
                    boolean huffman = !DO_NOT_HUFFMAN.contains(header);
                    encodeName(buffer, never_index ? (byte)0x10 : (byte)0x00, 4, header.asString(), name);
                    encodeValue(buffer,huffman,field.getValue());

                    if (log.isDebugEnabled())
                        encoding="Lit"+
                                ((name == null) ? "HuffN" : ("IdxN" + (name.isStatic() ? "S" : "") + (1 + NBitInteger.octectsNeeded(4, context.index(name))))) +
                                (huffman ? "HuffV" : "LitV") +
                                (indexed ? "Idx" : (never_index ? "!!Idx" : "!Idx"));
                } else if (header == HttpHeader.CONTENT_LENGTH && field.getValue().length() > 1) {
                    // Non indexed content length for 2 digits or more
                    indexed = false;
                    encodeName(buffer, (byte)0x00, 4, header.asString(), name);
                    encodeValue(buffer, true,field.getValue());
                    if (log.isDebugEnabled())
                        encoding = "LitIdxNS" + (1 + NBitInteger.octectsNeeded(4, context.index(name))) + "HuffV!Idx";
                } else {
                    // indexed
                    indexed = true;
                    boolean huffman = !DO_NOT_HUFFMAN.contains(header);
                    encodeName(buffer, (byte)0x40, 6, header.asString(), name);
                    encodeValue(buffer, huffman, field.getValue());
                    if (log.isDebugEnabled())
                        encoding = ((name == null) ? "LitHuffN" : ("LitIdxN" + (name.isStatic() ? "S" : "") + 
                        		(1 + NBitInteger.octectsNeeded(6, context.index(name))))) +
                                (huffman ? "HuffVIdx" : "LitVIdx");
                }
            }

            // If we want the field referenced, then we add it to our
            // table and reference set.
            if (indexed)
                context.add(field);
        }

        if (log.isDebugEnabled()) {
            int e = buffer.position();
            if (log.isDebugEnabled())
                log.debug("encode {}:'{}' to '{}'", encoding, field, TypeUtils.toHexString(buffer.array(), buffer.arrayOffset() + p, e - p));
        }
    }

    private void encodeName(ByteBuffer buffer, byte mask, int bits, String name, Entry entry) {
        buffer.put(mask);
        if (entry == null) {
            // leave name index bits as 0
            // Encode the name always with lowercase huffman
            buffer.put((byte)0x80);
            NBitInteger.encode(buffer, 7, Huffman.octetsNeededLC(name));
            Huffman.encodeLC(buffer, name);
        } else {
            NBitInteger.encode(buffer, bits, context.index(entry));
        }
    }

	static void encodeValue(ByteBuffer buffer, boolean huffman, String value) {
		if (huffman) {
			// huffman literal value
			buffer.put((byte) 0x80);
			NBitInteger.encode(buffer, 7, Huffman.octetsNeeded(value));
			Huffman.encode(buffer, value);
		} else {
			// add literal assuming iso_8859_1
			buffer.put((byte) 0x00);
			NBitInteger.encode(buffer, 7, value.length());
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				if (c < ' ' || c > 127)
					throw new IllegalArgumentException();
				buffer.put((byte) c);
			}
		}
	}

}
