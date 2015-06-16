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
package com.redhat.fuse.patch.test;

import java.io.File;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.fuse.patch.internal.Parser;
import com.redhat.fuse.patch.internal.Parser.Metadata;
import com.redhat.fuse.patch.test.subA.ClassA;

public class BuildRefTest {
	
	static String inpath = "target/A1.jar";
	
	@BeforeClass
	public static void setUp() throws Exception {
		JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
		archive.addAsResource("propsA1.properties", "propsA.properties");
		archive.addClasses(ClassA.class);
		File target = new File(inpath);
		archive.as(ZipExporter.class).exportTo(target, true);
	}

	@Test
	public void testBuildRef() throws Exception {
		
		File outfile = new Parser().buildMetadataFile(new File(inpath), null);
		Assert.assertTrue("Is file: " + outfile, outfile.isFile());
		
		Metadata metadata = Parser.parseMetadata(outfile);
		Map<String, Long> entries = metadata.getEntries();
		Assert.assertEquals("" + Parser.VERSION, metadata.getVersion());
		Assert.assertEquals(2, entries.size());
		Assert.assertTrue(entries.get("com/redhat/fuse/patch/test/subA/ClassA.class") > 0L);
		Assert.assertTrue(entries.get("propsA.properties") > 0L);
	}
}
