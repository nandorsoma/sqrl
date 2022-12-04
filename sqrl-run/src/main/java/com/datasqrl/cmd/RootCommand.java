package com.datasqrl.cmd;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@CommandLine.Command(name = "datasqrl", mixinStandardHelpOptions = true, version = "discover 0.1",
        subcommands = {CompilerCommand.class, RunCommand.class, DiscoverCommand.class})
public class RootCommand implements Runnable {

    @CommandLine.Option(names = {"-p", "--package-file"}, description = "Package file")
    protected List<Path> packageFiles = Collections.EMPTY_LIST;

    @Override
    public void run() {
        System.out.println("Chose one of the commands: compile, run, or discover");
    }
}