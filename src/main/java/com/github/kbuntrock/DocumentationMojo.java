package com.github.kbuntrock;


import com.github.kbuntrock.configuration.ApiConfiguration;
import com.github.kbuntrock.yaml.Logger;
import com.github.kbuntrock.yaml.YamlWriter;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Goal which touches a timestamp file.
 */
@Mojo(name = "documentation", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class DocumentationMojo extends AbstractMojo {

    /**
     * A list of api configurations
     */
    @Parameter(required = true)
    private List<ApiConfiguration> apis;

    /**
     * Location of the file.
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    private ClassLoader projectClassLoader;

    public void execute() throws MojoExecutionException, MojoFailureException {
        Logger.INSTANCE.setLogger(getLog());

        validateConfiguration();

        scanProjectResources();
    }

    private void validateConfiguration() throws MojoFailureException {
        if (apis == null || apis.isEmpty()) {
            throw new MojoFailureException("At least one api configuration element should be configured");
        }
        for (ApiConfiguration apiConfiguration : apis) {
            if (apiConfiguration.getLocations() == null || apiConfiguration.getLocations().isEmpty()) {
                throw new MojoFailureException("At least one location element should be configured");
            }
        }
    }

    private void scanProjectResources() throws MojoFailureException, MojoExecutionException {

        projectClassLoader = createProjectDependenciesClassLoader();

        for (ApiConfiguration apiConfiguration : apis) {
            SpringResourceParser springResourceParser = new SpringResourceParser(projectClassLoader, apiConfiguration.getLocations());
            getLog().debug("Prepare to scan");
            TagLibrary tagLibrary = springResourceParser.scanRestControllers();
            getLog().debug("Scan done");
            String filePath = outputDirectory + "\\" + apiConfiguration.getFilename() + ".yml";
            getLog().debug("Prepared to write : " + filePath);
            try {
                File generatedFile = new File(filePath);
                new YamlWriter(projectClassLoader, project, apiConfiguration).write(new File(filePath), tagLibrary);

                if (apiConfiguration.isAttachArtifact()) {
                    projectHelper.attachArtifact(project, "yml", apiConfiguration.getFilename(), generatedFile);
                }

                int nbTagsGenerated = tagLibrary.getTags().size();
                int nbOperationsGenerated = tagLibrary.getTags().stream().map(t -> t.getEndpoints().size()).collect(Collectors.summingInt(Integer::intValue));
                getLog().info(apiConfiguration.getFilename() + " : " + nbTagsGenerated + " tags and " + nbOperationsGenerated + " operations generated.");
            } catch (IOException e) {
                throw new MojoFailureException("Cannot write file " + filePath);
            }
        }

    }

    /**
     * Create a classloader for the classes and dependencies of the project
     *
     * @return the classloader to use
     * @throws MojoExecutionException
     */
    private ClassLoader createProjectDependenciesClassLoader() throws MojoExecutionException {
        try {
            List<URL> pathUrls = new ArrayList<>();
            for (String compileClasspathElements : project.getCompileClasspathElements()) {
                pathUrls.add(new File(compileClasspathElements).toURI().toURL());
            }
            for (String runtimeClasspathElement : project.getRuntimeClasspathElements()) {
                pathUrls.add(new File(runtimeClasspathElement).toURI().toURL());
            }


            URL[] urlsForClassLoader = pathUrls.toArray(new URL[pathUrls.size()]);
            getLog().debug("urls for URLClassLoader: " + Arrays.asList(urlsForClassLoader));

            // We need to define parent classloader which is the parent of the plugin classloader, in order to not mix up
            // the project and the plugin classes.
//            projectClassLoader = new URLClassLoader(urlsForClassLoader, DocumentationMojo.class.getClassLoader().getParent());
            return new URLClassLoader(urlsForClassLoader, DocumentationMojo.class.getClassLoader());
        } catch (DependencyResolutionRequiredException | MalformedURLException ex) {
            throw new MojoExecutionException("Cannot create project dependencies classloader", ex);
        }

    }

}