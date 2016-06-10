package org.wildfly.extras.patch.internal;

import java.io.File;

import org.kohsuke.args4j.Option;

final class SelfExecutingOptions {
    @Option(name = "--help", help = true)
    boolean help;

    @Option(name = "--server", usage = "Path to the target server directory. Defaults to $JBOSS_HOME or $CWD.")
    File serverHome;

    @Option(name = "--query-repository", usage = "Query the repository for available patches")
    boolean queryRepository;

    @Option(name = "--query-server", usage = "Query the server for installed patches")
    boolean queryServer;

    @Option(name = "--query-server-paths", usage = "Query managed server paths")
    String queryServerPaths;

    @Option(name = "--audit-log", usage = "Print the audit log")
    boolean auditLog;

    @Option(name = "--install", forbids = { "--update", "--uninstall" },  usage = "Install the given patch id to the server")
    String installId;

    @Option(name = "--update", forbids = { "--install", "--uninstall" },  usage = "Update the server for the given patch name")
    String updateName;

    @Option(name = "--uninstall", forbids = { "--install", "--update" },  usage = "Uninstall the given patch id from the server")
    String uninstallId;

    @Option(name = "--force", usage = "Force an --install or --update operation")
    boolean force;
}
