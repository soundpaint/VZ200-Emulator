package emulator.z80;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private static final String ATTRIBUTE_NAME_ADDRESS = "address";
  private static final String ATTRIBUTE_NAME_LENGTH = "length";
  private static final String TAG_NAME_ANNOTATIONS = "annotations";
  private static final String TAG_NAME_META = "meta";
  private static final String TAG_NAME_AT = "at";
  private static final String TAG_NAME_LABEL = "label";
  private static final String TAG_NAME_HEADER = "header";
  private static final String TAG_NAME_FOOTER = "footer";
  private static final String TAG_NAME_COMMENT = "comment";
  private static final String TAG_NAME_BR = "br";
  private static final String TAG_NAME_DATA_BYTES = "data-bytes";

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
    private int length;
    private String mnemonic;

    private DataBytesRange() {
      throw new UnsupportedOperationException("unsupported constructor");
    }

    public DataBytesRange(int address, int length, String mnemonic) {
      if (length <= 0) {
        throw new IllegalArgumentException("length > 0: " + length);
      }
      firstAddress = address & MAX_ADDRESS;
      int lastAddress = firstAddress + length - 1;
      if (lastAddress <= MAX_ADDRESS) {
        primaryLastAddress = lastAddress;
        wrappedLastAddress = -1;
      } else {
        primaryLastAddress = MAX_ADDRESS;
        wrappedLastAddress = lastAddress & MAX_ADDRESS;
      }
      this.length = length;
      this.mnemonic = mnemonic;
    }

    public String getMnemonic() {
      return mnemonic;
    }

    public boolean startsWith(int address) {
      address &= MAX_ADDRESS;
      return address == firstAddress;
    }

    public boolean contains(int address) {
      address &= MAX_ADDRESS;
      return
        ((address >= firstAddress) && (address <= primaryLastAddress)) ||
        (address <= wrappedLastAddress);
    }

    public int getRemainingDataBytes(int address) {
      address &= MAX_ADDRESS;
      int offs = (address - firstAddress) & 0xffff;
      int remainingDataBytes = length - offs;
      if (remainingDataBytes < 1) {
        String message = String.format("address %i not range %s",
                                       address, this);
        throw new IllegalArgumentException(message);
      }
      return remainingDataBytes;
    }
  }

  private Map<Integer, String> adr2label;
  private Map<String, Integer> label2adr;
  private Map<Integer, List<String>> adr2header;
  private Map<Integer, List<String>> adr2footer;
  private Map<Integer, List<String>> adr2comment;
  private Map<Integer, DataBytesRange> adr2dbRange;
  private Element meta;

  public Annotations() {
    label2adr = new HashMap<String, Integer>();
    adr2label = new HashMap<Integer, String>();
    adr2header = new HashMap<Integer, List<String>>();
    adr2footer = new HashMap<Integer, List<String>>();
    adr2comment = new HashMap<Integer, List<String>>();
    adr2dbRange = new TreeMap<Integer, DataBytesRange>();
    meta = null;
  }

  public void clear() {
    label2adr.clear();
    adr2label.clear();
    adr2header.clear();
    adr2footer.clear();
    adr2comment.clear();
    adr2dbRange.clear();
  }

  public void addLabel(int address, String label) {
    if (adr2label.containsKey(address)) {
      String strAddress = Util.hexShortStr(address);
      System.out.println("WARNING: Annotations: " +
                         "redefining label for address " + strAddress);
    }
    adr2label.put(address, label);
    label2adr.put(label, address);
  }

  public String getLabel(int address) {
    return adr2label.get(address);
  }

  public int resolveLabel(String label) {
    return label2adr.get(label);
  }

  public void removeLabel(int address) {
    if (adr2label.containsKey(address)) {
      String label = adr2label.remove(address);
      label2adr.remove(label);
    }
  }

  public void addHeader(int address, List<String> text) {
    if (adr2header.containsKey(address)) {
      String strAddress = Util.hexShortStr(address);
      System.out.println("WARNING: Annotations: " +
                         "redefining header for address " + strAddress);
    }
    adr2header.put(address, text);
  }

  public List<String> getHeader(int address) {
    return adr2header.get(address);
  }

  public void removeHeader(int address) {
    if (adr2header.containsKey(address)) {
      adr2header.remove(address);
    }
  }

  public void addFooter(int address, List<String> text) {
    if (adr2footer.containsKey(address)) {
      String strAddress = Util.hexShortStr(address);
      System.out.println("WARNING: Annotations: " +
                         "redefining footer for address " + strAddress);
    }
    adr2footer.put(address, text);
  }

  public List<String> getFooter(int address) {
    return adr2footer.get(address);
  }

  public void removeFooter(int address) {
    if (adr2footer.containsKey(address)) {
      adr2footer.remove(address);
    }
  }

  public void addComment(int address, List<String> text) {
    if (adr2comment.containsKey(address)) {
      String strAddress = Util.hexShortStr(address);
      System.out.println("WARNING: Annotations: " +
                         "redefining comment for address " + strAddress);
    }
    adr2comment.put(address, text);
  }

  public List<String> getComment(int address) {
    return adr2comment.get(address);
  }

  public void removeComment(int address) {
    if (adr2comment.containsKey(address)) {
      adr2comment.remove(address);
    }
  }

  private void checkForClash(int address, int length) {
    // TODO: Performance!!!
    for (int i = 0; i < length; i++) {
      if (isDataByte(address + i)) {
        String strAddress = Util.hexShortStr(address + i);
        System.out.println("WARNING: Annotations: " +
                           "data bytes range clash for address " +
                           strAddress);
      }
    }
  }

  public void addDataBytesRange(int address, int length, String mnemonic) {
    checkForClash(address, length);
    if (adr2dbRange.containsKey(address)) {
      String strAddress = Util.hexShortStr(address);
      System.out.println("WARNING: Annotations: " +
                         "redefining data bytes range for address " +
                         strAddress);
    }
    adr2dbRange.put(address, new DataBytesRange(address, length, mnemonic));
  }

  public void removeDataBytesRange(int address) {
    if (adr2dbRange.containsKey(address)) {
      adr2dbRange.remove(address);
    }
  }

  public boolean isDataByte(int address) {
    for (DataBytesRange range : adr2dbRange.values()) {
      if (range.contains(address)) {
        return true;
      }
    }
    return false;
  }

  public int getRemainingDataBytes(int address) {
    for (DataBytesRange range : adr2dbRange.values()) {
      if (range.contains(address)) {
        return range.getRemainingDataBytes(address);
      }
    }
    return 0;
  }

  public String getDataBytesMnemonic(int address) {
    for (DataBytesRange range : adr2dbRange.values()) {
      // TODO: Performance: replace linear search with
      // hashed lookup
      String mnemonic = range.getMnemonic();
      if (range.startsWith(address)) {
        return mnemonic;
      } else if (range.contains(address)) {
        return mnemonic != null ? "" : null;
      }
    }
    return null;
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

  private List<String> parseMultiLineText(Element element)
    throws ParseException
  {
    StringBuffer line = new StringBuffer();
    String trimmedLine;
    List<String> lines = new ArrayList<String>();
    final NodeList childNodes = element.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        if (childElementName.equals(TAG_NAME_BR)) {
          lines.add(line.toString());
          line.setLength(0);
        } else {
          throw new ParseException(childElement, "unexpected element: " +
                                   childElementName);
        }
      } else if (childNode instanceof Text) {
        String trimmedText = childNode.getTextContent().trim();
        if (!trimmedText.isEmpty()) {
          if (line.length() > 0) {
            line.append(" ");
          }
          line.append(trimmedText);
        }
      } else if (isWhiteSpace(childNode)) {
        // ignore white space
      } else if (isIgnorableNodeType(childNode)) {
        // ignore comments, entities, etc.
      } else {
        throw new ParseException(childNode, "unsupported node");
      }
    }
    lines.add(line.toString());
    return lines;
  }

  private void parseDataBytes(Element element, int address)
    throws ParseException
  {
    StringBuffer line = null;
    int dataBytes =
      parseShort(element,
                 element.getAttribute(ATTRIBUTE_NAME_LENGTH)) & 0xffff;
    final NodeList childNodes = element.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        throw new ParseException(childElement, "unexpected element: " +
                                 childElementName);
      } else if (childNode instanceof Text) {
        String trimmedText = childNode.getTextContent().trim();
        if (!trimmedText.isEmpty()) {
          if (line == null) {
            line = new StringBuffer();
          }
          if (line.length() > 0) {
            line.append(" ");
          }
          line.append(trimmedText);
        }
      } else if (isWhiteSpace(childNode)) {
        // ignore white space
      } else if (isIgnorableNodeType(childNode)) {
        // ignore comments, entities, etc.
      } else {
        throw new ParseException(childNode, "unsupported node");
      }
    }
    addDataBytesRange(address, dataBytes,
                      line != null ? line.toString() : null);
  }

  private void parseMeta(Element element) throws ParseException {
    throw new ParseException(element, "not yet implemented");
  }

  private void parseAt(Element element) throws ParseException {
    int address =
      parseShort(element,
                 element.getAttribute(ATTRIBUTE_NAME_ADDRESS)) & 0xffff;
    String label = null;
    final NodeList childNodes = element.getChildNodes();
    for (int index = 0; index < childNodes.getLength(); index++) {
      final Node childNode = childNodes.item(index);
      if (childNode instanceof Element) {
        final Element childElement = (Element)childNode;
        final String childElementName = childElement.getTagName();
        if (childElementName.equals(TAG_NAME_LABEL)) {
          if (label != null) {
            throwDuplicateException(childElement, TAG_NAME_LABEL);
          }
          label = childElement.getTextContent();
          addLabel(address, label);
        } else if (childElementName.equals(TAG_NAME_HEADER)) {
          List<String> lines = parseMultiLineText(childElement);
          addHeader(address, lines);
        } else if (childElementName.equals(TAG_NAME_FOOTER)) {
          List<String> lines = parseMultiLineText(childElement);
          addFooter(address, lines);
        } else if (childElementName.equals(TAG_NAME_COMMENT)) {
          List<String> lines = parseMultiLineText(childElement);
          addComment(address, lines);
        } else if (childElementName.equals(TAG_NAME_DATA_BYTES)) {
          parseDataBytes(childElement, address);
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
        } else if (childElementName.equals(TAG_NAME_AT)) {
          parseAt(childElement);
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
