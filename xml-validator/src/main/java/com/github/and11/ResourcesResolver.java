package com.github.and11;

import org.apache.xml.resolver.tools.CatalogResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;

public class ResourcesResolver implements LSResourceResolver, EntityResolver {

    public static final Logger logger = LoggerFactory.getLogger(ResourcesResolver.class);

    private final CatalogResolver res;

    public ResourcesResolver(CatalogResolver res) {
        this.res = res;
    }

    private void setEntityResolver(SAXSource source) throws TransformerException {
        XMLReader reader = source.getXMLReader();
        if (reader == null) {
            SAXParserFactory spFactory = SAXParserFactory.newInstance();
            spFactory.setNamespaceAware(true);
            try {
                reader = spFactory.newSAXParser().getXMLReader();
            } catch (ParserConfigurationException ex) {
                throw new TransformerException(ex);
            } catch (SAXException ex) {
                throw new TransformerException(ex);
            }
        }
        reader.setEntityResolver(this);
        source.setXMLReader(reader);
    }

    private LSInput newLSInput(String publicId, String systemId, String resource) {
        SAXSource source = new SAXSource();
        source.setInputSource(new InputSource(resource));
        try {
            setEntityResolver(source);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }


        return new LSInputImpl(publicId, systemId, source.getInputSource());
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {

        logger.debug("resolveResource type: {}, namespaceURI: {}, publicId: {}, systemId: {}, baseURI: {}",
                type, namespaceURI, publicId, systemId, baseURI);

        String resolved = null;

        if (systemId != null) {
            try {
                logger.debug("resolving by systemId: {}", systemId);
                resolved = res.getCatalog().resolveSystem(systemId);
                if (resolved != null) {
                    logger.debug("successfully resolved by systemId as {}", resolved);
                    return newLSInput(publicId, resolved, resolved);
                }
                else {
                    logger.debug("systemId resolution failed");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        logger.debug("resolving by uri: {}", namespaceURI);
        try {
            resolved = res.getCatalog().resolveURI(namespaceURI);
            if (resolved != null) {
                logger.debug("successfully resolved by URI as {}", resolved);
                return newLSInput(publicId, resolved, resolved);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        logger.error("resolution failed");
        return null;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return res.resolveEntity(publicId, systemId);
    }
}
