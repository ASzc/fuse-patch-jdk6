package org.wildfly.extras.patch.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.internal.MetadataParser;

public class Main {
    public static void main(String[] args) throws IOException {
        String zipPath = null;
        String patchName = null;
        String patchVersion = null;
        try {
            zipPath = args[0];
            patchName = args[1];
            patchVersion = args[2];
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Required parameters: ZIP_PATH PATCH_NAME PATCH_VERSION");
            System.exit(2);
        }

        File zip = new File(zipPath);
        try {
            addMetadata(zip, patchName, patchVersion);
        } catch (FileNotFoundException e) {
            System.err.println("Zip file " + zip + " not found: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void addMetadata(File zipFile, String patchName, String patchVersion) throws IOException {
        FileInputStream zipFileInputStream = null;
        try {
            zipFileInputStream = new FileInputStream(zipFile);
            ZipInputStream input = new ZipInputStream(zipFileInputStream);

            File replacementZipFile = new File(zipFile.getPath() + ".replacement");
            FileOutputStream zipFileOutputStream = null;
            try {
                zipFileOutputStream = new FileOutputStream(replacementZipFile);
                ZipOutputStream output = new ZipOutputStream(zipFileOutputStream);

                Map<String, Long> entries = copyAndEnumerateZipEntries(input, output);
                Patch patch = createPatchFromZipEntries(entries, patchName, patchVersion);

                appendMetadataEntries(output, patch);
                output.close();
            } finally {
                if (zipFileOutputStream != null)
                    try {
                        zipFileOutputStream.close();
                    } catch (IOException e) {
                    }
            }
        } finally {
            if (zipFileInputStream != null)
                try {
                    zipFileInputStream.close();
                } catch (IOException e) {
                }
        }
        // TODO move/replace orig if no error
    }

    public static Map<String, Long> copyAndEnumerateZipEntries(ZipInputStream input, ZipOutputStream output)
            throws IOException {
        Map<String, Long> ret = new TreeMap<String, Long>();
        byte[] buffer = new byte[2 ^ 20];
        ZipEntry entry;
        CRC32 crc = new CRC32();
        while ((entry = input.getNextEntry()) != null) {
            output.putNextEntry(entry);
            if (!entry.isDirectory()) {
                int currentRead;
                while ((currentRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, currentRead);
                    crc.update(buffer, 0, currentRead);
                }
                output.closeEntry();
                // Can't use the zip's CRC, as that's of the compressed data
                ret.put(entry.getName(), crc.getValue());
                crc.reset();
            }
        }
        return ret;
    }

    public static Patch createPatchFromZipEntries(Map<String, Long> entries, String patchName, String patchVersion) {
        PatchId patchId = PatchId.create(patchName, patchVersion);
        Collection<Record> records = new ArrayList<Record>(entries.size());
        for (Entry<String, Long> entry : entries.entrySet()) {
            Record record = Record.create(patchId, Action.INFO, new File(entry.getKey()), entry.getValue());
            records.add(record);
        }
        return Patch.create(new PatchMetadataBuilder().patchId(patchId).build(), records);
    }

    public static void appendMetadataEntries(ZipOutputStream output, Patch patch) throws IOException {
        output.putNextEntry(new ZipEntry("fusepatch/"));
        output.putNextEntry(new ZipEntry("fusepatch/repository/"));
        output.putNextEntry(new ZipEntry("fusepatch/workspace/"));
        output.putNextEntry(new ZipEntry("fusepatch/workspace/audit.log"));
        output.putNextEntry(new ZipEntry("fusepatch/workspace/managed-paths.metadata"));
        writePatchManagedPaths(output, patch);

        PatchId pid = patch.getPatchId();
        output.putNextEntry(new ZipEntry("fusepatch/workspace/" + pid.getName() + "/" + pid.getVersion() + "/"
                + pid.getCanonicalForm() + ".metadata"));
        MetadataParser.writePatch(patch, output, true);

        output.closeEntry();
    }

    public static void writePatchManagedPaths(OutputStream output, Patch patch) throws IOException {
        List<PatchId> owners = new LinkedList<PatchId>();
        owners.add(patch.getPatchId());
        String ownersSuffix = " " + owners.toString();

        PrintStream writer = new PrintStream(output);
        for (Record record : patch.getRecords()) {
            writer.print(record.getPath());
            writer.println(ownersSuffix);
        }
    }
}
