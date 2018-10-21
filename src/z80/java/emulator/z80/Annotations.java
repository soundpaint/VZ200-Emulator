package emulator.z80;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Entity;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Notation;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

public class Annotations {
  private static final String SCHEMA_RESOURCE_NAME = "./annotations.xsd";
  private static final String TAG_NAME_ANNOTATIONS = "annotations";
  private static final String TAG_NAME_META = "meta";
  private static final String TAG_NAME_LABEL = "label";
  private static final String TAG_NAME_COMMENT = "comment";
  private static final String TAG_NAME_DB = "db";
  private static final String TAG_NAME_AT = "at";
  private static final String TAG_NAME_TEXT = "text";
  private static final String TAG_NAME_LENGTH = "length";

  private static boolean isIgnorableNodeType(final Node node)
  {
    return
      (node instanceof Comment) ||
      (node instanceof Entity) ||
      (node instanceof Notation) ||
      (node instanceof ProcessingInstruction);
  }

  private static boolean isWhiteSpace(final Node node)
  {
    if (!(node instanceof Text)) {
      return false;
    }
    final Text text = (Text)node;
    return text.getData().trim().isEmpty();
  }

  private static class DataBytesRange {
    // TODO: 0xffff is z80 specific
    private static final int MAX_ADDRESS = 0xffff;

    private int firstAddress;
    private int primaryLastAddress;
    private int wrappedLastAddress;

    public DataBytesRange(int address, int length) {
      firstAddress = address & MAX_ADDRESS;
      int lastAddress = firstAddress + length - 1;
      if (lastAddress <= MAX_ADDRESS) {
        primaryLastAddress = lastAddress;
        wrappedLastAddress = -1;
      } else {
        primaryLastAddress = MAX_ADDRESS;
        wrappedLastAddress = lastAddress & MAX_ADDRESS;
      }
    }

    public boolean contains(int address) {
      address &= MAX_ADDRESS;
      return
        ((address >= firstAddress) && (address <= primaryLastAddress)) ||
        (address <= wrappedLastAddress);
    }
  }

  private Map<Integer, String> labels;
  private Map<Integer, String> comments;
  private Map<Integer, DataBytesRange> dataBytesRanges;
  private Element meta;

  public Annotations() {
    labels = new HashMap<Integer, String>();
    comments = new HashMap<Integer, String>();
    dataBytesRanges = new TreeMap<Integer, DataBytesRange>();
    meta = null;
  }

  public void clear() {
    labels.clear();
    comments.clear();
    dataBytesRanges.clear();
  }

  public void addLabel(int address, String text) {
    labels.put(address, text);
  }

  public String getLabel(int address) {
    return labels.get(address);
  }

  public void removeLabel(int address) {
    labels.remove(address);
  }

  public void addComment(int address, String text) {
    comments.put(address, text);
  }

  public String getComment(int address) {
    return comments.get(address);
  }

  public void removeComment(int address) {
    comments.remove(address);
  }

  public void addDataBytesRange(int address, int length) {
    // TODO: Check for clash with previously added ranges.
    dataBytesRanges.put(address, new DataBytesRange(address, length));
  }

  public void removeDataBytesRange(int address) {
    dataBytesRanges.remove(address);
  }

  public boolean isDataByte(int address) {
    for (DataBytesRange range : dataBytesRanges.values()) {
      if (range.contains(address)) {
        return true;
      }
    }
    return false;
  }

  private void throwDuplicateException(final Element element,
                                       final String tagName,
                                       final Throwable cause)
    throws ParseException
  {
    throw new ParseException(element, "duplicate '" + tagName + "' definition",
                             cause);
  }

  private void throwDuplicateException(final Element element,
                                       final String tagName)
    throws ParseException
  {
    throw new ParseException(element, "duplicate '" + tagName + "' definition");
  }

  private void throwDuplicateException(final Element element,
                                       final String tagName1,
                                       final String tagName2)
    throws ParseException
  {
    throw new ParseException(element, "can define only one of '" +
                             tagName1 + "' and '" + tagName2 + "', " +
                             "but not both");
  }

  private static boolean startsWithHexPrefix(final String value)
  {
    return value.startsWith("0x") || value.startsWith("-0x");
  }

  private static boolean startsWithBinPrefix(final String value)
  {
    return value.startsWith("0b") || value.startsWith("-0b");
  }

  private static String dropPrefix(final String value)
  {
    return
      value.startsWith("-") ?
      "-" + value.substring(3) :
      value.substring(2);
  }

  private static short parseShort(final Element element, final String value)
    throws ParseException
  {
    final String trimmedValue = value.trim();
    final String lowerCaseValue = trimmedValue.toLowerCase();
    try {
      if (startsWithHexPrefix(lowerCaseValue)) {
        return Short.parseShort(dropPrefix(trimmedValue), 16);
      } else if (startsWithBinPrefix(lowerCaseValue)) {
        return Short.parseShort(dropPrefix(trimmedValue), 2);
      } else {
        return Short.parseShort(trimmedValue);
      }
    } catch (final NumberFormatException e) {
      throw new ParseException(element, "invalid short value: " + value);
    }
  }

  private void parseMeta(Element element) throws ParseException {
    throw new ParseException(element, "not yet implemented");
  }

  private void parseLabel(Element element) throws ParseException {
    Integer at = null;
    String text = null;
    final NodeList childNodes = element.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        if (childElementName.equals(TAG_NAME_AT)) {
          if (at != null) {
            throwDuplicateException(childElement, TAG_NAME_AT);
          }
          // TODO: 0xffff is z80 specific
          at = parseShort(childElement, childElement.getTextContent()) & 0xffff;
        } else if (childElementName.equals(TAG_NAME_TEXT)) {
          if (text != null) {
            throwDuplicateException(childElement, TAG_NAME_TEXT);
          }
          text = childElement.getTextContent();
        } else {
          throw new ParseException(childElement, "unexpected element: " +
                                   childElementName);
        }
      } else if (isWhiteSpace(childNode)) {
        // ignore white space
      } else if (isIgnorableNodeType(childNode)) {
        // ignore comments, entities, etc.
      } else {
        throw new ParseException(childNode, "unsupported node");
      }
    }
    addLabel(at, text);
  }

  private void parseComment(Element element) throws ParseException {
    Integer at = null;
    String text = null;
    final NodeList childNodes = element.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        if (childElementName.equals(TAG_NAME_AT)) {
          if (at != null) {
            throwDuplicateException(childElement, TAG_NAME_AT);
          }
          // TODO: 0xffff is z80 specific
          at = parseShort(childElement, childElement.getTextContent()) & 0xffff;
        } else if (childElementName.equals(TAG_NAME_TEXT)) {
          if (text != null) {
            throwDuplicateException(childElement, TAG_NAME_TEXT);
          }
          text = childElement.getTextContent();
        } else {
          throw new ParseException(childElement, "unexpected element: " +
                                   childElementName);
        }
      } else if (isWhiteSpace(childNode)) {
        // ignore white space
      } else if (isIgnorableNodeType(childNode)) {
        // ignore comments, entities, etc.
      } else {
        throw new ParseException(childNode, "unsupported node");
      }
    }
    addComment(at, text);
  }

  private void parseDB(Element element) throws ParseException {
    Integer at = null;
    Integer length = null;
    final NodeList childNodes = element.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        if (childElementName.equals(TAG_NAME_AT)) {
          if (at != null) {
            throwDuplicateException(childElement, TAG_NAME_AT);
          }
          // TODO: 0xffff is z80 specific
          at = parseShort(childElement, childElement.getTextContent()) & 0xffff;
        } else if (childElementName.equals(TAG_NAME_LENGTH)) {
          if (length != null) {
            throwDuplicateException(childElement, TAG_NAME_LENGTH);
          }
          // TODO: 0xffff is z80 specific
          length =
            parseShort(childElement, childElement.getTextContent()) & 0xffff;
        } else {
          throw new ParseException(childElement, "unexpected element: " +
                                   childElementName);
        }
      } else if (isWhiteSpace(childNode)) {
        // ignore white space
      } else if (isIgnorableNodeType(childNode)) {
        // ignore comments, entities, etc.
      } else {
        throw new ParseException(childNode, "unsupported node");
      }
    }
    addDataBytesRange(at, length);
  }

  private void parse(Document document) throws ParseException {
    final Element documentElement = document.getDocumentElement();
    final String documentName = documentElement.getTagName();
    if (!documentName.equals(TAG_NAME_ANNOTATIONS)) {
      throw new ParseException("expected document element '" +
                               TAG_NAME_ANNOTATIONS + "', but found " +
                               documentName);
    }
    final NodeList childNodes = documentElement.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        if (childElementName.equals(TAG_NAME_META)) {
          if (meta != null) {
            final Throwable cause =
              new ParseException(meta, "first definition here");
            cause.fillInStackTrace();
            throwDuplicateException(childElement, TAG_NAME_META, cause);
          }
          parseMeta(childElement);
          meta = childElement;
        } else if (childElementName.equals(TAG_NAME_LABEL)) {
          parseLabel(childElement);
        } else if (childElementName.equals(TAG_NAME_COMMENT)) {
          parseComment(childElement);
        } else if (childElementName.equals(TAG_NAME_DB)) {
          parseDB(childElement);
        } else {
          throw new ParseException(childElement, "unexpected element: " +
                                   childElementName);
        }
      } else if (isWhiteSpace(childNode)) {
        // ignore white space
      } else if (isIgnorableNodeType(childNode)) {
        // ignore comments, entities, etc.
      } else {
        throw new ParseException(childNode, "unsupported node");
      }
    }
  }

  public void loadFromResource(URL resourceUrl) throws ParseException {
    URL schemaUrl = Annotations.class.getResource(SCHEMA_RESOURCE_NAME);
    if (schemaUrl == null) {
      throw new ParseException("failed determining URL of annotations schema file");
    }
    System.out.println("using annotations XML schema: " + schemaUrl);
    try {
      Document document = LineNumberXmlParser.parse(resourceUrl, schemaUrl);
      parse(document);
    } catch (ParseException e) {
      throw new ParseException("failed loading annotations for resource " +
                               resourceUrl + ": " + e.getMessage());
    }
  }

  public void loadFromResource(String resourcePath) throws ParseException {
    URL resourceUrl;
    try {
      resourceUrl = new URL(resourcePath);
    } catch (MalformedURLException e) {
      throw
        new ParseException("failed loading annotations: bad resource path: " +
                           resourcePath, e);
    }
    loadFromResource(resourceUrl);
  }

  public void loadFromFile(File file) throws ParseException {
    URL resourceUrl;
    try {
      resourceUrl = file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new ParseException("failed loading annotations: bad file path: " +
                               file.getPath(), e);
    }
    loadFromResource(resourceUrl);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
