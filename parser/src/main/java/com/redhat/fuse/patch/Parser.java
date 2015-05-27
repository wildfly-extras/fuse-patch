/*
 * #%L
 * Fuse Patch :: Parser
 * %%
 * Copyright (C) 2015 Private
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.redhat.fuse.patch;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.fuse.patch.internal.IOUtils;
import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;
import com.redhat.fuse.patch.utils.IllegalStateAssertion;

public final class Parser {

    private static Logger log = LoggerFactory.getLogger(Parser.class);
    
    private final Options options;
    
    Parser(Options options) {
        this.options = options;
    }

    public static Map<String, Long> parseMetadata(File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(infile, "infile");
        IllegalArgumentAssertion.assertTrue(infile.isFile(), "Cannot find file: " + infile);
    	Map<String, Long> result = new LinkedHashMap<>();
    	BufferedReader br = new BufferedReader(new FileReader(infile));
    	try {
    		String line = br.readLine();
    		while (line != null) {
    			String[] toks = line.split("[\\s]");
    	        IllegalStateAssertion.assertEquals(2, toks.length, "Invalid line: " + line);
    			result.put(toks[0], new Long(toks[1]));
    			line = br.readLine();
    		} 
    	} finally {
    		br.close();
    	}
    	return Collections.unmodifiableMap(result);
    }

	public File buildMetadata(File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(infile, "infile");
        IllegalArgumentAssertion.assertTrue(infile.isFile(), "Cannot find file: " + infile);
        
		File outfile;
		if (options.ref != null) {
			outfile = options.ref.toFile();
		} else {
	    	String inpath = infile.getPath();
	    	int dotindex = inpath.lastIndexOf(".");
			String prefix = inpath.substring(0, dotindex);
			String outpath = prefix + ".metadata";
			outfile = new File(outpath);
		}
    	
    	List<String> lines = new ArrayList<>();
		ZipInputStream zip = new ZipInputStream(new FileInputStream(infile));
		try {
			byte[] buffer = new byte[1024];
			ZipEntry entry = zip.getNextEntry();
			while (entry != null) {
				if (!entry.isDirectory()) {
					String name = entry.getName();
					int read = zip.read(buffer);
					while (read > 0) {
						read = zip.read(buffer);
					}
					long crc = entry.getCrc();
					lines.add(name + " " + crc);
				}
				entry = zip.getNextEntry();
			}
		} finally {
			zip.close();
		}
		
		outfile.getParentFile().mkdirs();
		
		PrintWriter pw = new PrintWriter(outfile);
		try {
			Collections.sort(lines);
			for (String line : lines) {
				pw.println(line);
			}
		} finally {
			pw.close();
		}
		
		String message = "Patch metadata generated: " + outfile;
		System.out.println(message);
		log.info(message);
		
		return outfile;
	}

	public File buildPatch(File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(infile, "infile");
        IllegalArgumentAssertion.assertTrue(infile.isFile(), "Cannot find file: " + infile);
        
    	IllegalStateAssertion.assertNotNull(options.ref, "Ref cannot be null");
    	
    	Map<String, Long> meatadata = parseMetadata(options.ref.toFile());
    	
    	// Compute outpath
    	String inpath = infile.getPath();
    	int dotindex = inpath.lastIndexOf(".");
    	String suffix = inpath.substring(dotindex);
		String outpath = inpath.substring(0, dotindex) + "-fusepatch" + suffix;
		
		File outfile = new File(outpath);
		ZipOutputStream outzip = new ZipOutputStream(new FileOutputStream(outfile));
    	try {
    		ZipInputStream inzip = new ZipInputStream(new FileInputStream(infile));
    		try {
    			byte[] buffer = new byte[1024];
    			ZipEntry entry = inzip.getNextEntry();
    			while (entry != null) {
    				if (!entry.isDirectory()) {
    					ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    					String name = entry.getName();
    					int read = inzip.read(buffer);
    					while (read > 0) {
    						baos.write(buffer, 0, read);
    						read = inzip.read(buffer);
    					}
    					long crc = entry.getCrc();
    					
    					Long checksum = meatadata.get(name);
    					if (checksum == null || !checksum.equals(crc)) {
    						outzip.putNextEntry(entry);
    						IOUtils.writeWithFlush(outzip, baos.toByteArray());
    					} else {
    						log.debug("Skip entry: {}", name);
    					}
    				}
    				entry = inzip.getNextEntry();
    			}
    		} finally {
    			inzip.close();
    		}
    	} finally {
    		outzip.close();
    	}
    	
		String message = "Patch generated: " + outfile;
		System.out.println(message);
		log.info(message);
		
    	return outfile;
	}
}
