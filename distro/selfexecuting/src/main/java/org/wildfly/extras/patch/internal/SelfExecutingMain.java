package org.wildfly.extras.patch.internal;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

public class SelfExecutingMain {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            mainInternal(args);
        } catch (Throwable th) {
            Runtime.getRuntime().exit(1);
        }
    }

    // Entry point with no system exit
    public static void mainInternal(String[] args) throws Exception {
        SelfExecutingOptions options = new SelfExecutingOptions();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException ex) {
            parser.printUsage(System.err);
            throw ex;
        }

        try {
            run(parser, options);
        } catch (PatchException ex) {
            LOG.error("ERROR {}", ex.getMessage());
            LOG.debug("Patch Exception", ex);
            throw ex;
        } catch (Throwable th) {
            LOG.error("Error executing command", th);
            throw th;
        }
    }

    private static void run(CmdLineParser cmdParser, SelfExecutingOptions options) throws IOException, JAXBException {
        // Configure the patch tool builder
        PatchToolBuilder builder = new PatchToolBuilder();
        //System.out.println(SelfExecutingMain.class.getClassLoader().getResource("repository/"));
        builder.repositoryURL(SelfExecutingMain.class.getClassLoader().getResource("repository/"));
        if (options.serverHome != null) {
            builder.serverPath(options.serverHome);
        }

        boolean opfound = false;

        // Query the repository
        if (options.queryRepository) {
            PatchTool patchTool = builder.build();
            printPatches(patchTool.getRepository().queryAvailable(null));
            opfound = true;
        }

        // Query the server
        if (options.queryServer) {
            PatchTool patchTool = builder.serverPath(options.serverHome).build();
            printPatches(patchTool.getServer().queryAppliedPatches());
            opfound = true;
        }

        // Query the server paths
        if (options.queryServerPaths != null) {
            PatchTool patchTool = builder.serverPath(options.serverHome).build();
            List<String> managedPaths = new ArrayList<>();
            for (ManagedPath managedPath : patchTool.getServer().queryManagedPaths(options.queryServerPaths)) {
                managedPaths.add(managedPath.toString());
            }
            printLines(managedPaths);
            opfound = true;
        }

        // Install to server
        if (options.installId != null) {
            PatchTool patchTool = builder.serverPath(options.serverHome).build();
            patchTool.getServer().cleanUp();
            patchTool.install(PatchId.fromString(options.installId), options.force);
            opfound = true;
        }

        // Update the server
        if (options.updateName != null) {
            PatchTool patchTool = builder.serverPath(options.serverHome).build();
            patchTool.getServer().cleanUp();
            patchTool.update(options.updateName, options.force);
            opfound = true;
        }

        // Uninstall patch from server
        if (options.uninstallId != null) {
            PatchTool patchTool = builder.serverPath(options.serverHome).build();
            patchTool.getServer().cleanUp();
            patchTool.uninstall(PatchId.fromString(options.uninstallId));
            opfound = true;
        }

        // Print the audit log
        if (options.auditLog) {
            PatchTool patchTool = builder.serverPath(options.serverHome).build();
            printLines(patchTool.getServer().getAuditLog());
            opfound = true;
        }

        // Show help screen
        if (!opfound) {
            cmdParser.printUsage(System.err);
        }
    }

    private static void printLines(List<String> lines) {
        for (String line : lines) {
            System.out.println(line);
        }
    }

    private static void printPatches(List<PatchId> patches) {
        for (PatchId patchId : patches) {
            System.out.println(patchId.toString());
        }
    }
}
