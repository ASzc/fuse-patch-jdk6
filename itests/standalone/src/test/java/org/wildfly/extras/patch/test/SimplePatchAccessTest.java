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

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.gravia.resource.ManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.Server;

@RunWith(Arquillian.class)
public class SimplePatchAccessTest {

    @Deployment
    public static JavaArchive deployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "patch-access-test");
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
    public void testSimpleUpdate() throws Exception {
        PatchTool patchTool = new PatchToolBuilder().build();
        Repository repository = patchTool.getRepository();
        Server server = patchTool.getServer();

        PatchId pid = PatchId.fromString("fuse-patch-distro-wildfly-" + PatchTool.VERSION);
        List<PatchId> pids = repository.queryAvailable(null);

        // With the feature pack runtime we need to install the fuse-patch package
        if (pids.size() == 0) {
            URL fileUrl = new URL(repository.getBaseURL() + "/" + pid + ".zip");
            Assert.assertEquals(pid, repository.addArchive(fileUrl));
            Package upd = patchTool.update("fuse-patch-distro-wildfly", true);
            Assert.assertEquals(pid, upd.getPatchId());
        }
        pids = repository.queryAvailable(null);
        Assert.assertEquals(1, pids.size());
        Assert.assertEquals(pid, pids.get(0));

        pids = server.queryAppliedPackages();
        Assert.assertEquals(1, pids.size());
        Assert.assertEquals(pid, pids.get(0));
    }
}
