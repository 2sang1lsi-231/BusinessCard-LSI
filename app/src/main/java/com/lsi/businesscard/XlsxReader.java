package com.lsi.businesscard;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

public final class XlsxReader {
    public static List<Map<String,String>> readFirstSheet(File file) throws Exception {
        ZipFile zip = new ZipFile(file);
        try {
            List<String> shared = readSharedStrings(zip);
            String sheetPath = firstSheetPath(zip);
            ZipEntry sheetEntry = zip.getEntry(sheetPath);
            if (sheetEntry == null) throw new IllegalArgumentException("첫 번째 시트를 찾을 수 없습니다.");
            Document doc = parse(readAll(zip.getInputStream(sheetEntry)));
            NodeList rowNodes = doc.getElementsByTagName("row");
            if (rowNodes.getLength() == 0) return new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();
            for (int r=0; r<rowNodes.getLength(); r++) {
                Element row = (Element) rowNodes.item(r);
                NodeList cells = row.getElementsByTagName("c");
                List<String> values = new ArrayList<>(); int sequential = 0;
                for (int i=0;i<cells.getLength();i++) {
                    Element c = (Element) cells.item(i); String ref = c.getAttribute("r");
                    int col = ref == null || ref.isEmpty() ? sequential : columnIndex(ref);
                    while (values.size() <= col) values.add("");
                    values.set(col, cellValue(c, shared)); sequential = col + 1;
                }
                rows.add(values);
            }
            List<String> headers = rows.get(0); List<Map<String,String>> out = new ArrayList<>();
            for (int r=1;r<rows.size();r++) {
                Map<String,String> m = new LinkedHashMap<>(); List<String> vals = rows.get(r); boolean any = false;
                for (int c=0;c<headers.size();c++) {
                    String h = headers.get(c) == null ? "" : headers.get(c).trim(); if (h.isEmpty()) continue;
                    String v = c < vals.size() ? vals.get(c) : ""; if (v != null && !v.trim().isEmpty()) any = true;
                    m.put(h, v == null ? "" : v);
                }
                if (any) out.add(m);
            }
            return out;
        } finally { zip.close(); }
    }

    private static String firstSheetPath(ZipFile zip) throws Exception {
        ZipEntry wb = zip.getEntry("xl/workbook.xml"); ZipEntry rel = zip.getEntry("xl/_rels/workbook.xml.rels");
        if (wb == null || rel == null) return "xl/worksheets/sheet1.xml";
        Document d=parse(readAll(zip.getInputStream(wb))); NodeList sheets=d.getElementsByTagName("sheet");
        if(sheets.getLength()==0)return "xl/worksheets/sheet1.xml";
        Element sh=(Element)sheets.item(0); String rid=sh.getAttribute("r:id");
        if(rid.isEmpty()) rid=sh.getAttribute("id");
        Document rd=parse(readAll(zip.getInputStream(rel))); NodeList rs=rd.getElementsByTagName("Relationship");
        for(int i=0;i<rs.getLength();i++){Element e=(Element)rs.item(i);if(rid.equals(e.getAttribute("Id"))){String t=e.getAttribute("Target");if(t.startsWith("/"))t=t.substring(1);else if(!t.startsWith("xl/"))t="xl/"+t;return normalizePath(t);}}
        return "xl/worksheets/sheet1.xml";
    }
    private static String normalizePath(String p){
        String[] xs=p.split("/"); java.util.ArrayDeque<String> st=new java.util.ArrayDeque<>();
        for(String x:xs){if(x.isEmpty()||".".equals(x))continue;if("..".equals(x)){if(!st.isEmpty())st.removeLast();}else st.addLast(x);} StringBuilder b=new StringBuilder();for(String x:st){if(b.length()>0)b.append('/');b.append(x);}return b.toString();
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        List<String> out = new ArrayList<>(); ZipEntry e = zip.getEntry("xl/sharedStrings.xml"); if (e == null) return out;
        Document d = parse(readAll(zip.getInputStream(e))); NodeList si = d.getElementsByTagName("si");
        for (int i=0;i<si.getLength();i++) { Element x=(Element)si.item(i); NodeList ts=x.getElementsByTagName("t"); StringBuilder sb=new StringBuilder(); for(int j=0;j<ts.getLength();j++) sb.append(ts.item(j).getTextContent()); out.add(sb.toString()); }
        return out;
    }

    private static String cellValue(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if ("inlineStr".equals(type)) { NodeList ts = c.getElementsByTagName("t"); StringBuilder sb = new StringBuilder(); for (int i=0;i<ts.getLength();i++) sb.append(ts.item(i).getTextContent()); return sb.toString(); }
        NodeList vs = c.getElementsByTagName("v"); if (vs.getLength()==0) return ""; String raw = vs.item(0).getTextContent();
        if ("s".equals(type)) { try { int ix=Integer.parseInt(raw); return ix>=0&&ix<shared.size()?shared.get(ix):raw; } catch(Exception ignore){ return raw; } }
        if ("b".equals(type)) return "1".equals(raw)?"TRUE":"FALSE";
        return raw;
    }

    private static int columnIndex(String ref) { int col=0,i=0; while(i<ref.length() && Character.isLetter(ref.charAt(i))) { col=col*26+(Character.toUpperCase(ref.charAt(i))-'A'+1); i++; } return Math.max(0,col-1); }
    private static byte[] readAll(InputStream in) throws Exception { try { ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)o.write(b,0,n); return o.toByteArray(); } finally { in.close(); } }
    private static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch(Exception ignore){}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch(Exception ignore){}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch(Exception ignore){}
        try { f.setXIncludeAware(false); } catch(Exception ignore){} f.setExpandEntityReferences(false); f.setNamespaceAware(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private XlsxReader() {}
}
