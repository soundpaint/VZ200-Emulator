package emulator.z80;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class ParseException extends Exception
{
  private static final long serialVersionUID = 8748895015695309559L;

  private final Node location;

  public ParseException()
  {
    this((Node)null);
  }

  public ParseException(final Node location)
  {
    this.location = location;
  }

  public ParseException(final String message)
  {
    this(null, message);
  }

  public ParseException(final Node location, final String message)
  {
    super(message);
    this.location = location;
  }

  public ParseException(final String message, final Throwable cause)
  {
    this(null, message, cause);
  }

  public ParseException(final Node location,
                        final String message, final Throwable cause)
  {
    super(concatMessages(message, cause), cause);
    this.location = location;
  }

  public ParseException(final Throwable cause)
  {
    this((Node)null, cause);
  }

  public ParseException(final Node location, final Throwable cause)
  {
    super(cause.getMessage(), cause);
    this.location = location;
  }

  public Node getLocation()
  {
    return location;
  }

  private static String concatMessages(final String message,
                                       final Throwable cause)
  {
    if (cause == null) {
      return message;
    }
    if ((message == null) || message.isEmpty()) {
      return cause.getMessage();
    }
    final String causeMessage = cause.getMessage();
    if ((causeMessage == null) || causeMessage.isEmpty()) {
      return message;
    }
    return message + ": " + causeMessage;
  }

  private static String formatLocation(final Node location)
  {
    if (location == null) {
      return null;
    }
    final Document document = location.getOwnerDocument();
    final Object inputSourceInfo =
      document.getUserData(LineNumberXmlParser.KEY_XML_URL);
    final Object columnInfo =
      location.getUserData(LineNumberXmlParser.KEY_COLUMN_NUMBER);
    final Object lineInfo =
      location.getUserData(LineNumberXmlParser.KEY_LINE_NUMBER);
    final String strInputSource =
      inputSourceInfo != null ? "" + inputSourceInfo : "";
    final String strColumn = columnInfo != null ? "column " + columnInfo : "";
    final String strLine = lineInfo != null ? "line " + lineInfo : "";
    final String strInputPosition =
      strColumn +
      ((columnInfo != null) && (lineInfo != null) ? ", " : "") +
      strLine;
    final String strLocation =
      strInputPosition + (!strInputPosition.isEmpty() ? " " : "") +
      (!strInputSource.isEmpty() ? "in " : "") +
      strInputSource;
    final String strDocumentPosition =
      "document position: " + document.compareDocumentPosition(location);
    return
      strLocation + (!strLocation.isEmpty() ? ", " : "") + strDocumentPosition;
  }

  public String getMessage()
  {
    final String superMessage = super.getMessage();
    final String formattedLocation = formatLocation(location);
    final String labelledLocation =
      (formattedLocation != null) && !formattedLocation.isEmpty() ?
      "location: " + formattedLocation : "";
    return
      superMessage +
      ((superMessage != null) && !superMessage.isEmpty() &&
       (labelledLocation != null) && !labelledLocation.isEmpty() ? ", " : "") +
      labelledLocation;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:java
 * End:
 */
