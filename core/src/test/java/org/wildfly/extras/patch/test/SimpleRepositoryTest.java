/*
 * #%L
 * Fuse Patch :: Core
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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PackageMetadata;
import org.wildfly.extras.patch.PackageMetadataBuilder;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.utils.IOUtils;

public class SimpleRepositoryTest {

    final static Path[] repoPaths = new Path[5];

    @BeforeClass
    public static void setUp() throws Exception {
        for (int i = 0; i < 5; i++) {
            repoPaths[i] = Paths.get("target/repos/SimpleRepositoryTest/repo" + (i + 1));
            IOUtils.rmdirs(repoPaths[i]);
            repoPaths[i].toFile().mkdirs();
        }
    }

    @Test
    public void testSimpleAccess() throws Exception {

        URL url = new URL("file:./" + repoPaths[0].toString());

        PatchTool patchTool = new PatchToolBuilder().localRepository(url).build();
        Repository repo = patchTool.getRepository();

        // Add archive foo-1.0.0
        PatchId patchId = repo.addArchive(Archives.getZipUrlFoo100());
        Package patchSet = repo.getPackage(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(4, patchSet.getRecords().size());

        // Add archive foo-1.1.0
        patchId = PatchId.fromString("foo-1.1.0");
        PackageMetadata metadata = new PackageMetadataBuilder().patchId(patchId).postCommands("bin/fusepatch.sh --query-server").build();
        DataHandler dataHandler = new DataHandler(new URLDataSource(Archives.getZipUrlFoo110()));
        patchId = repo.addArchive(metadata, dataHandler, false);
        patchSet = repo.getPackage(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patchSet.getPatchId());
        Assert.assertEquals(3, patchSet.getRecords().size());
        Assert.assertEquals(1, patchSet.getMetadata().getPostCommands().size());
        Assert.assertEquals("bin/fusepatch.sh --query-server", patchSet.getMetadata().getPostCommands().get(0));

        // Add archive foo-1.1.0 again
        patchId = repo.addArchive(Archives.getZipUrlFoo110());
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patchSet.getPatchId());

        // Query available
        List<PatchId> patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 2, patches.size());

        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(1));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.getLatestAvailable("foo"));
        Assert.assertNull(repo.getLatestAvailable("bar"));

        // Cannot remove non-existing archive
        try {
            repo.removeArchive(PatchId.fromString("xxx-1.0.0"));
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("not exist: xxx-1.0.0"));
        }

        // Remove archive
        Assert.assertTrue(repo.removeArchive(PatchId.fromString("foo-1.1.0")));
        patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 1, patches.size());

        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), repo.getLatestAvailable("foo"));
    }

    @Test
    public void testRepoUrlWithSpaces() throws Exception {

        Path path = Paths.get("target/repos/SimpleRepositoryTest", "repo && path");
        IOUtils.rmdirs(path);
        path.toFile().mkdirs();

        URL url = new URL("file:./target/repos/SimpleRepositoryTest/" + URLEncoder.encode("repo && path", "UTF-8"));
        PatchTool patchTool = new PatchToolBuilder().localRepository(url).build();
        Repository repo = patchTool.getRepository();

        // Add archive foo-1.0.0
        PatchId patchId = repo.addArchive(Archives.getZipUrlFoo100());
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchId);
    }

    @Test
    public void testFileMove() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[1]).build();
        Repository repo = patchTool.getRepository();

        // copy a file to the root of the repository
        Path zipPathA = Paths.get(Archives.getZipUrlFoo100().toURI());
        File targetFile = repoPaths[1].resolve(zipPathA.getFileName()).toFile();
        Files.copy(zipPathA, targetFile.toPath());

        PatchId patchId = repo.addArchive(targetFile.toURI().toURL());
        Package patchSet = repo.getPackage(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(4, patchSet.getRecords().size());

        // Verify that the file got removed
        Assert.assertFalse("File got removed", targetFile.exists());
    }

    @Test
    public void testOverlappingPaths() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[2]).build();
        Repository repo = patchTool.getRepository();

        repo.addArchive(Archives.getZipUrlFoo100());
        Path copyPath = Paths.get("target/foo-copy-1.1.0.zip");
        Files.copy(Paths.get(Archives.getZipUrlFoo110().toURI()), copyPath, REPLACE_EXISTING);
        URL fileUrl = copyPath.toUri().toURL();
        try {
            repo.addArchive(fileUrl);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("duplicate paths in [foo-1.0.0]"));
        }

        // Force
        PatchId patchId = PatchId.fromURL(fileUrl);
        patchId = repo.addArchive(fileUrl, true);
        Assert.assertEquals(PatchId.fromString("foo-copy-1.1.0"), patchId);
    }

    @Test
    public void testEqualOverlappingPaths() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[3]).build();
        Repository repo = patchTool.getRepository();

        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.addArchive(Archives.getZipUrlFoo110()));
        Assert.assertEquals(PatchId.fromString("bar-1.0.0"), repo.addArchive(Archives.getZipUrlBar100()));
    }

    @Test
    public void testAddOneOff() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[4]).build();
        Repository repo = patchTool.getRepository();

        URL url100sp1 = Archives.getZipUrlFoo100SP1();
        PatchId pid100sp1 = PatchId.fromURL(url100sp1);
        PatchId oneoffId = PatchId.fromString("foo-1.0.0");
        PackageMetadata md100sp1 = new PackageMetadataBuilder().patchId(pid100sp1).oneoffId(oneoffId).build();
        DataHandler data100sp1 = new DataHandler(new URLDataSource(url100sp1));

        try {
            repo.addArchive(md100sp1, data100sp1, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            // expected
        }

        PatchId pid100 = repo.addArchive(Archives.getZipUrlFoo100());
        Package pack100 = repo.getPackage(pid100);
        repo.addArchive(md100sp1, data100sp1, false);
        Package pack100sp1 = repo.getPackage(pid100sp1);
        Archives.assertPathsEqual(pack100.getRecords(), pack100sp1.getRecords());
        Assert.assertEquals(0, pack100sp1.getMetadata().getDependencies().size());

        Package smartSet = Package.smartDelta(pack100, pack100sp1);
        Assert.assertEquals(1, smartSet.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartSet.getRecords().get(0));
    }
}
