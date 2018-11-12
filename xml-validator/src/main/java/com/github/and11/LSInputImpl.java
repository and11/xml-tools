package com.github.and11;

import org.w3c.dom.ls.LSInput;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class LSInputImpl implements LSInput {

    private String publicId;
    private String systemId;
    private InputSource resource;

    public LSInputImpl(String publicId, String systemId, InputSource resource) {
        this.publicId = publicId;
        this.systemId = systemId;
        this.resource = resource;
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    @Override
    public Reader getCharacterStream() {
        return null;
    }

    @Override
    public void setCharacterStream(Reader characterStream) {
    }

    @Override
    public InputStream getByteStream() {
        return resource.getByteStream();
    }

    @Override
    public void setByteStream(InputStream byteStream) {
    }

    @Override
    public String getStringData() {
        return null;
    }

    @Override
    public void setStringData(String stringData) {
    }

    @Override
    public String getBaseURI() {
        return null;
    }

    @Override
    public void setBaseURI(String baseURI) {
    }

    @Override
    public String getEncoding() {
        return StandardCharsets.UTF_8.name();
    }

    @Override
    public void setEncoding(String encoding) {
    }

    @Override
    public boolean getCertifiedText() {
        return false;
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {
    }
}
