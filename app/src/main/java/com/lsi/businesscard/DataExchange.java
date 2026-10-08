package com.lsi.businesscard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable exports: text cells preserve phone prefixes and never execute Excel formulas. */
public final class DataExchange {
    public static final String[][] EXTRA = {{"수정일","updated_at"},{"그룹","group_name"},{"가져온 곳","source"},{"만난 날짜","met_at"},{"만난 장소","met_place"},{"만남 기록","meeting_notes"}};
    public static List<String[]> columns(){List<String[]> out=new ArrayList<>();out.addAll(Arrays.asList(CamCardImporter.MAP));out.addAll(Arrays.asList(EXTRA));out.add(new String[]{"즐겨찾기","favorite"});return out;}
    private static String value(Contact c,String col){return col.equals("favorite")?String.valueOf(c.favorite):c.get(col);}
    public static void writeXlsx(File file,List<Contact> contacts)throws Exception{
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
            entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(z,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"명함\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(z,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));Writer w=new OutputStreamWriter(z,StandardCharsets.UTF_8);List<String[]> cols=columns();
            w.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><sheetData>");
            for(int row=0;row<=contacts.size();row++){w.write("<row r=\""+(row+1)+"\">");for(int col=0;col<cols.size();col++){String val=row==0?cols.get(col)[0]:value(contacts.get(row-1),cols.get(col)[1]);w.write("<c r=\""+column(col)+(row+1)+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+xml(val)+"</t></is></c>");}w.write("</row>");}
            w.write("</sheetData><autoFilter ref=\"A1:"+column(cols.size()-1)+(contacts.size()+1)+"\"/></worksheet>");w.flush();z.closeEntry();
        }
    }
    public static void writeCsv(File file,List<Contact> contacts)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){w.write('\ufeff');List<String[]> cols=columns();for(int row=0;row<=contacts.size();row++){for(int col=0;col<cols.size();col++){if(col>0)w.write(',');String s=row==0?cols.get(col)[0]:value(contacts.get(row-1),cols.get(col)[1]);if(!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0)s="'"+s;w.write('"'+s.replace("\"","\"\"")+'"');}w.write("\r\n");}}}
    public static void writeVcard(File file,List<Contact> contacts)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){for(Contact c:contacts){line(w,"BEGIN:VCARD");line(w,"VERSION:3.0");line(w,"FN:"+v(nz(c.get("name"),c.get("company1"))));line(w,"N:"+v(c.get("name"))+";;;;");prop(w,"ORG",join(" ",c.get("company1"),c.get("department1")));prop(w,"TITLE",c.get("title1"));for(int i=1;i<=3;i++){prop(w,"TEL;TYPE=CELL",c.get("mobile"+i));prop(w,"TEL;TYPE=WORK",c.get("phone"+i));prop(w,"TEL;TYPE=WORK,FAX",c.get("fax"+i));prop(w,"EMAIL;TYPE=INTERNET",c.get("email"+i));if(!c.get("address"+i).isEmpty())line(w,"ADR;TYPE=WORK:;;"+v(c.get("address"+i))+";;;;");}prop(w,"URL",c.get("website"));prop(w,"CATEGORIES",c.get("group_name"));prop(w,"NOTE",join("\n",c.get("note1"),c.get("note2"),c.get("note3"),c.get("met_at"),c.get("met_place"),c.get("meeting_notes")));line(w,"END:VCARD");}}}
    private static void prop(Writer w,String key,String val)throws Exception{if(!val.isEmpty())line(w,key+":"+v(val));}
    // Fold on UTF-8 byte boundaries, including Korean text (RFC 2426).
    private static void line(Writer w,String s)throws Exception{int bytes=0;for(int i=0;i<s.length();){int cp=s.codePointAt(i);String part=new String(Character.toChars(cp));int n=part.getBytes(StandardCharsets.UTF_8).length;if(bytes+n>75){w.write("\r\n ");bytes=1;}w.write(part);bytes+=n;i+=Character.charCount(cp);}w.write("\r\n");}
    private static String v(String s){return s.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n").replace("\n","\\n").replace(";","\\;").replace(",","\\,");}
    private static String xml(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();){int c=s.codePointAt(i);i+=Character.charCount(c);if(c==9||c==10||c==13||c>=32&&c!=0xfffe&&c!=0xffff)b.appendCodePoint(c);}return b.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void entry(ZipOutputStream z,String name,String s)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(s.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String column(int n){String s="";for(n++;n>0;n=(n-1)/26)s=(char)('A'+(n-1)%26)+s;return s;}
    private static String nz(String a,String b){return a.isEmpty()?b:a;}
    private static String join(String sep,String...parts){StringBuilder b=new StringBuilder();for(String s:parts)if(!s.isEmpty()){if(b.length()>0)b.append(sep);b.append(s);}return b.toString();}
    private DataExchange(){}
}
