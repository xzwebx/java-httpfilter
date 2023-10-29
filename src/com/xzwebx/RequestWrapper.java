package com.xzwebx;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.Charset;

public class RequestWrapper extends HttpServletRequestWrapper {
    String default_charset = "utf-8";

    private final byte[] body;

    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        ServletInputStream inputStream = request.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len=0;
        while ((len = inputStream.read(buf))!=-1){
            out.write(buf,0,len);
        }
        out.flush();
        this.body = out.toByteArray();
        out.close();
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyString() {
        return new String(body, Charset.forName(default_charset));
    }

    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(this.body);
        return new ServletInputStream() {
            public int read() throws IOException {
                return inputStream.read();
            }

            public boolean isFinished() {
                return false;
            }

            public boolean isReady() {
                return false;
            }
            public void setReadListener(ReadListener readListener) {}
        };
    }

}