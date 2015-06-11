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
package com.redhat.fuse.patch.internal;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.fuse.patch.SmartPatch;
import com.redhat.fuse.patch.Version;
import com.redhat.fuse.patch.utils.IOUtils;
import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;
import com.redhat.fuse.patch.utils.IllegalStateAssertion;

public final class Parser {

    private static final Logger LOG = LoggerFactory.getLogger(Parser.class);
    
    public static Version VERSION;
    static {
        InputStream input = SmartPatch.class.getResourceAsStream("version.properties");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            VERSION = Version.parseVersion(reader.readLine());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            IOUtils.safeClose(input);
        }
    }
    
    static final String VERSION_PREFIX = "# fusepatch:";
    
    public static Metadata parseMetadata(File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(infile, "infile");
        IllegalArgumentAssertion.assertTrue(infile.isFile(), "Cannot find file: " + infile);
        
        Metadata result;
    	BufferedReader br = new BufferedReader(new FileReader(infile));
    	try {
    		String line = br.readLine();
    		IllegalStateAssertion.assertTrue(line.startsWith(VERSION_PREFIX), "Cannot obtain version info");
    		result = new Metadata(line.substring(VERSION_PREFIX.length()).trim());
    		while (line != null) {
    			line = line.trim();
    			if (line.length() > 0 && !line.startsWith("#")) {
         			String[] toks = line.split("[\\s]");
        	        IllegalStateAssertion.assertEquals(2, toks.length, "Invalid line: " + line);
        			result.entries.put(toks[0], new Long(toks[1]));
    			}
    			line = br.readLine();
    		} 
    	} finally {
    		br.close();
    	}
    	return result;
    }

    /**
     * Build metadata from the given zip input
     * @param infile The required zip input file
     */
    public Metadata buildMetadata(File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(infile, "infile");
        IllegalArgumentAssertion.assertTrue(infile.isFile(), "Cannot find file: " + infile);
        
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
        Collections.sort(lines);

        Metadata metadata = new Metadata(VERSION.toString());
        for (String line : lines) {
            String[] toks = line.split(" ");
            
            metadata.entries.put(toks[0], Long.parseLong(toks[1]));
        }
        
        return metadata;
    }

    /**
     * Builds a metadata file
     * 
     * @param infile The required zip input file
     * @param outfile The optional target metadata file
     */
	public File buildMetadataFile(File infile, File outfile) throws IOException {
        Metadata metadata = buildMetadata(infile);
        if (outfile == null) {
            String inpath = infile.getPath();
            int dotindex = inpath.lastIndexOf(".");
            String prefix = inpath.substring(0, dotindex);
            String outpath = prefix + ".metadata";
            outfile = new File(outpath);
        }
		return buildMetadataFile(metadata, outfile);
	}

    /**
     * Builds a metadata file
     * 
     * @param metadata The required metadata
     * @param outfile The required target metadata file
     */
    public File buildMetadataFile(Metadata metadata, File outfile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(outfile, "outfile");
        
        outfile.getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(outfile);
        try {
            pw.println(VERSION_PREFIX + " " + VERSION);
            for (Entry<String, Long> entry : metadata.entries.entrySet()) {
                pw.println(entry.getKey() + " " + entry.getValue());
            }
        } finally {
            pw.close();
        }
        
        LOG.info("Patch metadata generated: " + outfile);
        return outfile;
    }
    
    public File buildPatch(File metaRef, File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(metaRef, "metaRef");
        Metadata metadata = parseMetadata(metaRef);
        return buildPatch(metadata, infile);
    }
    
	public File buildPatch(Metadata metadata, File infile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(metadata, "metadata");
        IllegalArgumentAssertion.assertNotNull(infile, "infile");
        IllegalArgumentAssertion.assertTrue(infile.isFile(), "Cannot find file: " + infile);
        
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
    					
    					Long checksum = metadata.entries.get(name);
    					if (checksum == null || !checksum.equals(crc)) {
    						outzip.putNextEntry(entry);
    						IOUtils.writeWithFlush(outzip, baos.toByteArray());
    					} else {
    						LOG.debug("Skip entry: {}", name);
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
    	
		LOG.info("Patch generated: " + outfile);
		
    	return outfile;
	}
	
	public static class Metadata {
		private final String version;
		private final Map<String, Long> entries = new LinkedHashMap<>();
		
		Metadata(String version) {
			this.version = version;
		}

		public String getVersion() {
			return version;
		}
		
		public Map<String, Long> getEntries() {
			return Collections.unmodifiableMap(entries);
		}
	}
}
