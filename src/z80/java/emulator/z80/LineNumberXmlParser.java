package emulator.z80;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Stack;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

public class LineNumberXmlParser
{
  private static final String KEY_LEXICAL_HANDLER =
    "http://xml.org/sax/properties/lexical-handler";

  public static final String KEY_XML_URL = "xml-url";
  public static final String KEY_COLUMN_NUMBER = "column-number";
  public static final String KEY_LINE_NUMBER = "line-number";
  public static final String KEY_PUBLIC_ID = "public-id";
  public static final String KEY_SYSTEM_ID = "system-id";

  public static Document parse(final URL xmlUrl, final URL schemaUrl)
    throws ParseException
  {
    final Document document = createDocument(xmlUrl, schemaUrl);
    final Handler handler = createHandler(document);
    final SAXParser parser = createParser(handler);
    try {
      parser.parse(xmlUrl.toString(), handler);
    } catch (final SAXException | IOException e) {
      throw new ParseException("failed parsing XML input", e);
    }
    return document;
  }

  private static Document createDocument(final URL xmlUrl,
                                         final URL schemaUrl)
    throws ParseException
  {
    final Document document;
    try {
      final DocumentBuilderFactory documentBuilderFactory =
        DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setIgnoringElementContentWhitespace(true);
      documentBuilderFactory.setNamespaceAware(true);
      documentBuilderFactory.
        setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                     XMLConstants.W3C_XML_SCHEMA_NS_URI);
      try {
        setSchema(documentBuilderFactory, schemaUrl, xmlUrl);
      } catch (final Exception t) {
        throw new ParseException("failed setting schema", t);
      }
      final DocumentBuilder documentBuilder =
        documentBuilderFactory.newDocumentBuilder();
      document = documentBuilder.newDocument();
    } catch (final ParserConfigurationException e) {
      throw new ParseException("failed creating DOM builder", e);
    }
    document.setUserData(KEY_XML_URL, xmlUrl, null);
    return document;
  }

  private static void
    setSchema(final DocumentBuilderFactory documentBuilderFactory,
              final URL schemaUrl, final URL xmlUrl)
    throws ParseException
  {
    if (schemaUrl != null) {
      // TODO: Support for XSD 1.1
      // ("http://www.w3.org/XML/XMLSchema/v1.1").
      final String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;

      final SchemaFactory schemaFactory =
        SchemaFactory.newInstance(schemaLanguage);
      final Schema schema;
      try {
        schema = schemaFactory.newSchema();
      } catch (final SAXException e) {
        throw new ParseException("generating new XSD instance failed", e);
      }
      final Validator validator = schema.newValidator();
      final XsdResourceResolver xsdResourceResolver =
        new XsdResourceResolver(schemaUrl);
      validator.setResourceResolver(xsdResourceResolver);
      InputSource inputSource;
      try {
        inputSource = new InputSource(xmlUrl.openStream());
      } catch (IOException e) {
        throw new ParseException("failed loading XML for validation", e);
      }
      SAXSource saxSource = new SAXSource(inputSource);
      try {
        validator.validate(saxSource);
      } catch (final IOException | SAXException e) {
        throw new ParseException("failed validating XML", e);
      }
    } else {
      System.out.println("[using no schema]");
    }
  }

  private static Handler createHandler(final Document document)
  {
    return new Handler(document);
  }

  private static class Handler extends DefaultHandler implements LexicalHandler
  {
    private final Document document;
    private final Stack<Element> treePath;
    private Locator locator;

    public Handler(final Document document)
    {
      this.document = document;
      treePath = new Stack<Element>();
      locator = null;
    }

    private Node getParentNode()
    {
      return !treePath.isEmpty() ? treePath.peek() : document;
    }

    @Override
    public InputSource resolveEntity(final String publicId,
                                     final String systemId)
    {
      return null; // nothing yet
    }

    @Override
    public void notationDecl(final String name,
                             final String publicId,
                             final String systemId)
    {
      // nothing yet
    }

    @Override
    public void unparsedEntityDecl(final String name,
                                   final String publicId,
                                   final String systemId,
                                   final String notationName)
    {
      // nothing yet
    }

    @Override
    public void setDocumentLocator(final Locator locator)
    {
      this.locator = locator;
    }

    @Override
    public void startDocument()
    {
      // nothing yet
    }

    @Override
    public void endDocument()
    {
      // nothing yet
    }

    @Override
    public void startPrefixMapping(final String prefix,
                                   final String uri)
    {
      // nothing yet
    }

    @Override
    public void endPrefixMapping(final String prefix)
    {
      // nothing yet
    }

    @Override
    public void startElement(final String uri, final String localName,
                             final String qName,
                             final Attributes attributes)
    {
      final Element element = document.createElement(qName);
      for (int index = 0; index < attributes.getLength(); index++) {
        element.setAttribute(attributes.getQName(index),
                             attributes.getValue(index));
      }
      element.setUserData(KEY_COLUMN_NUMBER,
                          locator.getColumnNumber(),
                          null);
      element.setUserData(KEY_LINE_NUMBER,
                          locator.getLineNumber(),
                          null);
      element.setUserData(KEY_PUBLIC_ID, locator.getPublicId(), null);
      element.setUserData(KEY_SYSTEM_ID, locator.getSystemId(), null);
      treePath.push(element);
    }

    @Override
    public void endElement(final String uri, final String localName,
                           final String qName)
    {
      final Element element = treePath.pop();
      final Node parentNode = getParentNode();
      parentNode.appendChild(element);
    }

    @Override
    public void characters(final char ch[], final int start,
                           final int length)
    {
      final Node parentNode = getParentNode();
      final String data = new String(ch, start, length);
      final Text text = document.createTextNode(data);
      parentNode.appendChild(text);
    }

    @Override
    public void ignorableWhitespace(final char ch[], final int start,
                                    final int length)
    {
      final Node parentNode = getParentNode();
      final String data = new String(ch, start, length);
      final Text text = document.createTextNode(data);
      parentNode.appendChild(text);
    }

    @Override
    public void processingInstruction(final String target,
                                      final String data)
    {
      final Node parentNode = getParentNode();
      final ProcessingInstruction processingInstruction =
        document.createProcessingInstruction(target, data);
      parentNode.appendChild(processingInstruction);
    }

    @Override
    public void skippedEntity(final String name)
    {
      // nothing yet
    }

    @Override
    public void warning(final SAXParseException e)
    {
      // nothing yet
    }

    @Override
    public void error(final SAXParseException e)
    {
      // nothing yet
    }

    @Override
    public void fatalError(final SAXParseException e)
    {
      // nothing yet
    }

    @Override
    public void startDTD(final String name,
                         final String publicId,
                         final String systemId)
    {
      // nothing yet
    }

    @Override
    public void endDTD()
    {
      // nothing yet
    }

    @Override
    public void startEntity(final String name)
    {
      // nothing yet
    }

    @Override
    public void endEntity(final String name)
    {
      // nothing yet
    }

    @Override
    public void startCDATA()
    {
      // nothing yet
    }

    @Override
    public void endCDATA()
    {
      // nothing yet
    }

    @Override
    public void comment(final char[] textChars,
                        final int start,
                        final int length)
    {
      final Node parentNode = getParentNode();
      final String text = new String(textChars, start, length);
      final Comment comment = document.createComment(text);
      parentNode.appendChild(comment);
    }
  }

  private static SAXParser createParser(final Handler handler)
    throws ParseException
  {
    final SAXParserFactory factory;
    factory = SAXParserFactory.newInstance();
    final SAXParser parser;
    try {
      parser = factory.newSAXParser();
    } catch (final SAXException | ParserConfigurationException e) {
      throw new ParseException("failed creating SAX parser", e);
    }
    try {
      parser.setProperty(KEY_LEXICAL_HANDLER, handler);
    } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
      throw new ParseException("failed configuring SAX parser", e);
    }
    return parser;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:java
 * End:
 */
