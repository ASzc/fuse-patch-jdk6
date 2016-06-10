package org.wildfly.extras.patch.repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.URLDataSource;

import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.internal.MetadataParser;

public class JarResourceRepository extends AbstractRepository {

    private static void loadPatches(URL repoURL, List<PatchId> availablePatchIds, Map<PatchId, Patch> availablePatches,
            HashMap<PatchId, URL> availablePatchUrls) {
        ClassLoader classloader = JarResourceRepository.class.getClassLoader();

        BufferedReader indexReader = null;
        try {
            indexReader = new BufferedReader(new InputStreamReader(repoURL.openStream()));
            String line;
            while ((line = indexReader.readLine()) != null) {
                String path = line.trim();
                if (path != "") {
                    String metadataPath = path + ".metadata";
                    URL patchMetadataUrl = classloader.getResource(metadataPath);
                    if (patchMetadataUrl == null)
                        throw new IllegalStateException("Indexed resource " + metadataPath + " is not accessible");

                    String patchPath = path + ".zip";
                    URL patchUrl = classloader.getResource(patchPath);
                    if (patchUrl == null)
                        throw new IllegalStateException("Indexed resource " + patchPath + " is not accessible");

                    PatchId patchId = PatchId.fromURL(patchUrl);
                    availablePatchIds.add(patchId);
                    availablePatchUrls.put(patchId, patchUrl);

                    BufferedReader metadataReader = new BufferedReader(new InputStreamReader(patchMetadataUrl.openStream()));
                    try {
                        Patch patch = MetadataParser.readPatch(metadataReader);
                        availablePatches.put(patchId, patch);
                    } finally {
                        metadataReader.close();
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                if (indexReader != null)
                    indexReader.close();
            } catch (IOException e) {
            }
        }
    }

    private List<PatchId> availablePatchIds;
    private HashMap<PatchId, Patch> availablePatches;
    private HashMap<PatchId, URL> availablePatchUrls;

    public JarResourceRepository(Lock lock, URL repoURL) {
        super(lock, repoURL);

        availablePatchIds = new LinkedList<PatchId>();
        availablePatches = new HashMap<PatchId, Patch>();
        availablePatchUrls = new HashMap<PatchId, URL>();
        loadPatches(repoURL, availablePatchIds, availablePatches, availablePatchUrls);
    }

    @Override
    public List<PatchId> queryAvailable(String prefix) {
        return availablePatchIds;
    }

    @Override
    public Patch getPatch(PatchId patchId) {
        return availablePatches.get(patchId);
    }

    @Override
    protected DataSource getDataSource(PatchId patchId) {
        return new URLDataSource(availablePatchUrls.get(patchId));
    }

    @Override
    public boolean removeArchive(PatchId removeId) {
        throw new UnsupportedOperationException("JAR resource repositories are read-only");
    }

    @Override
    protected PatchId addArchiveInternal(Patch patch, DataHandler dataHandler) throws IOException {
        throw new UnsupportedOperationException("JAR resource repositories are read-only");
    }
}
