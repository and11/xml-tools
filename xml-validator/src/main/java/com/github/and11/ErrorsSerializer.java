package com.github.and11;



import org.xml.sax.SAXParseException;

import java.util.List;

public class ErrorsSerializer {
    public String serialize(ValidationErrorHandler errorHandler){
        List<ValidationErrorHandler.ErrorRecord> errorRecords = errorHandler.getErrors();
        if (!errorRecords.isEmpty()) {
            final StringBuffer message = new StringBuffer();
            for (ValidationErrorHandler.ErrorRecord error : errorRecords) {
                appendMessage(message, error);
            }
            if (errorHandler.getErrorCount() + errorHandler.getFatalCount() > 0) {
                throw new RuntimeException(message.toString());
            } else {
                System.out.println(message.toString());
            }
            return message.toString();
        }

        return null;
    }

    private void appendMessage(StringBuffer messageBuffer, ValidationErrorHandler.ErrorRecord error) {
        SAXParseException e = error.getException();
        final String publicId = e.getPublicId();
        final String systemId = e.getSystemId();
        final int lineNum = e.getLineNumber();
        final int colNum = e.getColumnNumber();
        final String location;
        if (publicId == null && systemId == null && lineNum == -1 && colNum == -1) {
            location = "";
        } else {
            final StringBuffer loc = new StringBuffer();
            String sep = "";
            if (publicId != null) {
                loc.append("Public ID ");
                loc.append(publicId);
                sep = ", ";
            }
            if (systemId != null) {
                loc.append(sep);
                loc.append(systemId);
                sep = ", ";
            }
            if (lineNum != -1) {
                loc.append(sep);
                loc.append("line ");
                loc.append(lineNum);
                sep = ", ";
            }
            if (colNum != -1) {
                loc.append(sep);
                loc.append(" column ");
                loc.append(colNum);
                sep = ", ";
            }
            location = loc.toString();
        }

        messageBuffer.append(("".equals(location) ? "" : ", at " + location));
        messageBuffer.append(": ");
        messageBuffer.append(error.getType().toString());
        messageBuffer.append(": ");
        messageBuffer.append(e.getMessage());
        String lineSep = System.getProperty("line.separator");
        messageBuffer.append(lineSep);
    }

}
