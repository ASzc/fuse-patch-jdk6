package org.wildfly.extras.patch.repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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

public class JarResourceRepository extends AbstractRepository {

    private static void loadPatches(URL repoURL, List<PatchId> availablePatchIds, Map<PatchId, Patch> availablePatches,
            HashMap<PatchId, URL> availablePatchUrls) {
        String repoPrefix = repoURL.toString().split("!", 1)[0] + "!/";

        InputStream indexStream = null;
        try {
            indexStream = repoURL.openStream();

            BufferedReader indexReader = new BufferedReader(new InputStreamReader(indexStream));
            String line;
            while ((line = indexReader.readLine()) != null) {
                String path = line.trim();
                if (path != "") {
                    InputStream patchStream = JarResourceRepository.class.getClassLoader().getResourceAsStream(path);
                    if (patchStream == null) {
                        throw new IllegalStateException("Indexed patch resource " + path + " is not accessible");
                    } else {
                        // TODO
                        PatchId patchId = null;
                        Patch patch = null;

                        availablePatchIds.add(patchId);
                        availablePatches.put(patchId, patch);

                        URL patchUrl = new URL(repoPrefix + path);
                        availablePatchUrls.put(patchId, patchUrl);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                if (indexStream != null)
                    indexStream.close();
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
