/*
 * #%L
 * Fuse Patch :: Integration Tests :: Standalone
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

package org.wildfly.extras.patch.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.gravia.resource.ManifestBuilder;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PackageMetadata;
import org.wildfly.extras.patch.PackageMetadataBuilder;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.repository.RepositoryService;
import org.wildfly.extras.patch.test.subA.ClassA;
import org.wildfly.extras.patch.utils.IOUtils;

@RunWith(Arquillian.class)
public class RepositoryEndpointTest {

	@ArquillianResource
	private Deployer deployer;
	
    @Deployment
    public static JavaArchive deployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "repository-endpoint-test.jar");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                ManifestBuilder builder = new ManifestBuilder();
                builder.addManifestHeader("Dependencies", "org.wildfly.extras.patch");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testRepository() throws Exception {

    	// Configure JAX-WS Client
        URL wsdlUrl = new URL("http://localhost:8080/fuse-patch-jaxws/RepositoryEndpoint?wsdl");
        PatchTool patchTool = new PatchToolBuilder().jaxwsRepository(RepositoryService.SERVICE_QNAME, wsdlUrl).build();
        Repository repository = patchTool.getRepository();
        
        // Test repository base URL
        URL baseURL = repository.getBaseURL();
        Assert.assertEquals("file", baseURL.getProtocol());
        Assert.assertTrue("Unexpected path: " + baseURL, baseURL.getPath().endsWith("fusepatch/repository"));

        PatchId pid = PatchId.fromString("fuse-patch-distro-wildfly-" + PatchTool.VERSION);
        List<PatchId> pids = repository.queryAvailable(null);
        
        // With the feature pack runtime we need to install the fuse-patch package
        if (pids.size() == 0) {
            URL fileUrl = new URL(repository.getBaseURL() + "/" + pid + ".zip");
            Assert.assertEquals(pid, repository.addArchive(fileUrl));
        }
        
        // Verify queryAvailable
        pids = repository.queryAvailable(null);
        Assert.assertEquals(1, pids.size());
        Assert.assertEquals(pid, pids.get(0));

        // Verify getLatestAvailable
        Assert.assertEquals(pid, repository.getLatestAvailable("fuse-patch-distro-wildfly"));
        
        // Add foo-1.0.0
        URL fileUrl = getArchiveURL("foo-1.0.0");
        PatchId pid100 = repository.addArchive(fileUrl);
        Package pack100 = repository.getPackage(pid100);
        Assert.assertEquals(pid100, pack100.getPatchId());
        Assert.assertEquals(4, pack100.getRecords().size());
        Assert.assertEquals(0, pack100.getMetadata().getPostCommands().size());
        
        // Remove foo-1.0.0
        Assert.assertTrue(repository.removeArchive(pid100));
        Assert.assertNull(repository.getLatestAvailable("foo"));
        
        // Add foo-1.0.0
        fileUrl = getArchiveURL("foo-1.0.0");
        pid100 = repository.addArchive(fileUrl);
        pack100 = repository.getPackage(pid100);
        Assert.assertEquals(pid100, pack100.getPatchId());
        Assert.assertEquals(4, pack100.getRecords().size());
        Assert.assertEquals(0, pack100.getMetadata().getPostCommands().size());
        
        // Add foo-1.1.0
        fileUrl = getArchiveURL("foo-1.1.0");
        PatchId pid110 = PatchId.fromURL(fileUrl);
        PackageMetadata md110 = new PackageMetadataBuilder().patchId(pid110).postCommands("echo hell world").build();
        DataHandler data110 = new DataHandler(new URLDataSource(fileUrl));
        repository.addArchive(md110, data110, false);
        Package pack110 = repository.getPackage(pid110);
        Assert.assertEquals(pid110, pack110.getPatchId());
        Assert.assertEquals(3, pack110.getRecords().size());
        Assert.assertEquals(1, pack110.getMetadata().getPostCommands().size());
        
        // Install foo-1.0.0
        pack100 = patchTool.install(pid100, false);
        Assert.assertEquals(pid100, pack100.getPatchId());
        Assert.assertEquals(4, pack100.getRecords().size());
        Assert.assertEquals(0, pack100.getMetadata().getPostCommands().size());
    }

    private URL getArchiveURL(String name) throws IOException {
    	Path dataDir = Paths.get(System.getProperty("jboss.server.data.dir"));
    	Path patchDir = dataDir.resolve("fusepatch");
    	patchDir.toFile().mkdirs();
    	File patchFile = patchDir.resolve(name + ".zip").toFile();
    	if (!patchFile.isFile()) {
        	try (InputStream input = deployer.getDeployment(name)) {
        		IOUtils.copy(input, new FileOutputStream(patchFile));
        	}
    	}
		return patchFile.toURI().toURL();
	}

	/**
     * foo-1.0.0.zip
     * 
     * config/remove-me.properties
     * config/propsA.properties
     * config/propsB.properties
     * lib/foo-1.0.0.jar
     */
    @Deployment(name = "foo-1.0.0", managed = false, testable = false)
    public static GenericArchive foo100() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.0.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/archives/propsA1.properties")), "config/remove-me.properties");
        archive.add(new FileAsset(new File("src/test/resources/archives/propsA1.properties")), "config/propsA.properties");
        archive.add(new FileAsset(new File("src/test/resources/archives/propsB.properties")), "config/propsB.properties");
        return archive;
    }

    /**
     * foo-1.1.0.zip
     * 
     * config/propsA.properties
     * config/propsB.properties
     * lib/foo-1.1.0.jar
     */
    @Deployment(name = "foo-1.1.0", managed = false, testable = false)
    public static GenericArchive foo110() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.1.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/archives/propsA2.properties")), "config/propsA.properties");
        archive.add(new FileAsset(new File("src/test/resources/archives/propsB.properties")), "config/propsB.properties");
        return archive;
    }
}
