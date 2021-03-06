package com.github.and11;

import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ValidatorBuilder {

    private static final QName OASIS_CATALOG = new QName("urn:oasis:names:tc:entity:xmlns:xml:catalog","catalog");

    private static class RaisingErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    public static final Logger logger = LoggerFactory.getLogger(ValidatorBuilder.class);

    public ValidationErrorHandler createErrorHandler() {
        return new ValidationErrorHandler();
    }

    public static class XmlValidator {
        private final ErrorHandler errHandler;
        private final Validator validator;

        public XmlValidator(Validator validator, ErrorHandler errHandler) {
            this.validator = validator;
            this.errHandler = errHandler;
        }

        public ErrorHandler getErrHandler() {
            return errHandler;
        }

        public void validate(Path file) {
            try {
                logger.info("validating file {}", file);
                validator.validate(new StreamSource(file.toFile()));
                logger.info("validated file {}", file);
            } catch (SAXParseException e) {
                logger.debug("got exception: {}", e);
                try {
                    errHandler.fatalError(e);
                } catch (SAXException se) {
                    throw new RuntimeException("While parsing " + file + ": " + e.getMessage(), se);
                }
            } catch (Exception e) {
                throw new RuntimeException("While parsing " + file + ": " + e.getMessage(), e);
            }

        }
    }

    private final List<Path> catalogs = new ArrayList<>();

    public ValidatorBuilder addCatalogs(List<Path> catalogs) {
        this.catalogs.addAll(catalogs);
        return this;
    }

    private boolean isXMLFile(Path file) {
        return file.toFile().getName().endsWith(".xml");
    }

    public void scanCatalogs(Path directory) throws IOException {

        catalogs.addAll(Files.walk(directory).filter(Files::isRegularFile)
                .filter(this::isXMLFile)
                .filter(this::isXMLCatalog)
                .collect(Collectors.toList()));
    }

    private StartElement getRootElement(InputStream is) throws Exception {
        XMLEventReader parser = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            parser = factory.createXMLEventReader(is);
            while (parser.hasNext()) {
                XMLEvent event = parser.nextEvent();
                if (event.isStartElement()) {
                    return event.asStartElement();
                }
            }
            return null;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private boolean isXMLCatalog(Path path) {
        try(InputStream is = Files.newInputStream(path)) {
            StartElement element = getRootElement(is);
            return (element != null) && (OASIS_CATALOG.equals(element.getName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ErrorHandler errorHandler;

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public XmlValidator build() {

        try {
            CatalogResolver res = createResolver(catalogs);
            ResourcesResolver resourcesResolver = new ResourcesResolver(res);

            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schemaFactory.setResourceResolver(resourcesResolver);

            Schema schema = schemaFactory.newSchema();
            Validator validator = schema.newValidator();
            validator.setResourceResolver(resourcesResolver);

            if (errorHandler != null) {
                logger.info("using provided error handler");
                validator.setErrorHandler(errorHandler);
            } else {
                logger.info("using default error handler");
                validator.setErrorHandler(new RaisingErrorHandler());
            }

            return new XmlValidator(validator, validator.getErrorHandler());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CatalogResolver createResolver(List<Path> catalogs) throws IOException {

        CatalogManager manager = new CatalogManager();
        manager.setIgnoreMissingProperties(true);
        if (logger.isDebugEnabled()) {
            manager.setVerbosity(Integer.MAX_VALUE);
        }
        manager.setPreferPublic(true);

        CatalogResolver resolver = new CatalogResolver(manager);
        logger.info("creating resolver");
        for (Path catalog : catalogs) {
            logger.info("adding catalog {}", catalog);
            resolver.getCatalog().parseCatalog(catalog.toFile().getAbsolutePath());
        }

        return resolver;
    }
}
