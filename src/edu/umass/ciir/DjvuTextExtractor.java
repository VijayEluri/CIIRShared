package edu.umass.ciir;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.tools.bzip2.CBZip2InputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.sun.org.apache.xerces.internal.parsers.SAXParser;

/**
 * Reads Compressed DJVU view and outputs the text.
 * 
 * @author Jeff Dalton
 *
 */
public class DjvuTextExtractor extends DefaultHandler {

    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";
	
	private XMLReader m_parser;

	String m_prevTerm = "";
	// holds a word
	StringBuilder m_wordBuff = new StringBuilder();
	// holds a page
	StringBuilder m_contentBuff = new StringBuilder();
	
	// holds the previous page number.
	Integer m_lastPageNum = null;
	
	// a list of all pages parsed so far.
	ArrayList<String> m_pages = new ArrayList<String>();

	private boolean m_inWord = false;
	
	public DjvuTextExtractor()  
	throws Exception {
	    m_parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
	}
	
	public String extractTextCompressed(InputStream is) 
	throws Exception {
		// skip the first two bytes... because BZ2 compression is buggy.
		// see http://www.kohsuke.org/bzip2/
		is.read();
		is.read();
		is = new CBZip2InputStream(is);
		parse(is);
		m_contentBuff.setLength(0);
		for (String page : m_pages) {
			m_contentBuff.append(page + "\n");
		}
		return m_contentBuff.toString();
	}
	
	public void parseCompressedBook(InputStream is) 
	throws Exception {
		// skip the first two bytes... because BZ2 compression is buggy.
		// see http://www.kohsuke.org/bzip2/
		is.read();
		is.read();
		is = new CBZip2InputStream(is);
		
		parse(is);
	}
	
	public void extractPages(InputStream is) 
	throws Exception {
		parse(is);
	}
	
	public void parse(InputStream inputStream) 
	throws Exception {
		m_pages.clear();
		InputStream inStream = new BufferedInputStream(inputStream);
		try {
			m_parser.setContentHandler(this);
			m_parser.parse(new InputSource(inStream));
		} finally {
			try {
				inStream.close();
			} catch (Exception e) {
				
			}
		}
	}

	public ArrayList<String> getContentByPage() {
		return m_pages;
	}
	
	@Override
	public void startElement(java.lang.String uri, java.lang.String localName, java.lang.String qName, Attributes attributes) 
	throws SAXException {
		if (localName.equals("WORD")) {
			m_wordBuff.setLength(0);
			m_inWord  = true;
		}
		
		if (localName.equals("PARAM") &&  attributes.getValue("", "name").equals("PAGE")) {
			String name = attributes.getValue("", "value");
			int start = name.indexOf("_");
			int end = name.indexOf(".");
			if (start > -1 && end > -1) {
				name = name.substring(start+1, end);
			}
			if (m_lastPageNum != null) {
				m_pages.add(m_contentBuff.toString());
				m_contentBuff.setLength(0);
			}
			m_lastPageNum = Integer.parseInt(name);
		}
	}

	@Override
	public void endElement(String uri, String localName, String rawName) {
		if (localName.equals("WORD") && m_wordBuff.length() > 0) {
			
			// handle escaping of ampersands
			String curWord = m_wordBuff.toString();
			if (curWord.indexOf('&') >= 0) {
				curWord = removeEscapes(curWord);
			}
			
			m_inWord = false;
			// Fix up hyphenation at end of lines.     
			if (m_prevTerm.length() > 1 ) {
				CharSequence lastCharsOfPrevWord = m_prevTerm.subSequence(m_prevTerm.length()-2, m_prevTerm.length());
				if (lastCharsOfPrevWord.toString().matches("\\p{Lower}-")) {
					// remove hyphen and space for hyphenated words
					//System.out.println("replacing hyphen");
					m_prevTerm = m_prevTerm.substring(0, m_prevTerm.length()-1);
					curWord = m_prevTerm + curWord;
				} else {
					m_contentBuff.append(m_prevTerm + " ");
				}
			} else {
				m_contentBuff.append(m_prevTerm + " ");
			}
			
			m_prevTerm = curWord;
			
		} else if (localName.equals("BODY")) {
			m_pages.add(m_contentBuff.toString());
		}
	}
	
	public void characters(char[] ch, int start, int length) {
		if (m_inWord) {
			m_wordBuff.append(ch, start, length);
		}
	}

	
    private static String removeEscapes(String line) {
        return line.replaceAll("&(amp|AMP);","&").replaceAll("&apos;", "'");
    }
    
	
	public static void main(String[] args) 
	throws Exception {
		DjvuTextExtractor extractor = new DjvuTextExtractor();
		File file = new File("/usr/aubury/scratch1/jdalton/code/test/officialarmyregi19692unit_djvu.xml.bz2");
		FileInputStream is = new FileInputStream(file);
		extractor.parseCompressedBook(is);
		is.close();
		PrintWriter pw = new PrintWriter("/usr/aubury/scratch1/jdalton/code/test/officialarmyregi19692unit_djvu.text");
		pw.flush();
		pw.close();
		ArrayList<String> pages = extractor.getContentByPage();
		System.out.println(pages.size());
		
	}
}
