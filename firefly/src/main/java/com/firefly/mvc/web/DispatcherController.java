package com.firefly.mvc.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface DispatcherController {
	
	void dispatch(HttpServletRequest request, HttpServletResponse response);
	
}
