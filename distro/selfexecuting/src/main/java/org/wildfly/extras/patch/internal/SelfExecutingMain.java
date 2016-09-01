package org.wildfly.extras.patch.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.spi.OptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;

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
    public static void mainInternal(String[] args) throws Throwable {
        SelfExecutingOptions options = new SelfExecutingOptions();
        CmdLineParser parser = new CmdLineParser(options);

        // Remove any options marked for removal by patch creator
        removeOptions(parser);

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
        // See JarURLConnection
        builder.repositoryURL(SelfExecutingMain.class.getClassLoader().getResource("repository.index"));
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
            List<String> managedPaths = new ArrayList<String>();
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

    private static void removeOptions(CmdLineParser parser) throws IOException {
        HashSet<String> removeNames = new HashSet<String>();
        URL removalFile = SelfExecutingMain.class.getClassLoader().getResource("fp_option_removals");
        if (removalFile != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(removalFile.openStream()));
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line != "") {
                    // ex: --uninstall
                    removeNames.add(line);
                }
            }
        }

        LinkedList<OptionHandler<?>> toRemove = new LinkedList<OptionHandler<?>>();
        for (OptionHandler<?> option : parser.getOptions()) {
            if (removeNames.contains(option.option.toString())) {
                toRemove.add(option);
            }
        }

        for (OptionHandler<?> option : toRemove) {
            parser.getOptions().remove(option);
        }
    }
}
