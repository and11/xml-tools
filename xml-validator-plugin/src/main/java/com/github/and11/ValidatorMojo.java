package com.github.and11;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "validate", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.TEST)
public class ValidatorMojo extends AbstractMojo {

    public static final Logger logger = LoggerFactory.getLogger(ValidatorMojo.class);

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${project.build.directory}/schemas", required = true, readonly = true)
    private File workingDir;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    @Parameter(property = "xml.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> pomRemoteRepositories;

    protected MavenSession getSession() {
        return session;
    }

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private File baseDir;

    @Parameter
    private String schemaVersion;

    @Component
    private ArchiverManager archiverManager;

    @Parameter
    private ValidateFiles resources;

    @Parameter
    private SchemaArtifacts schemas;

    @Parameter
    private String[] excludes;

    public File getBaseDir() {
        return baseDir;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skip) {
            logger.info("xml validation skipped by user (xml.stip property is true)");
            return;
        }

        try {

            ArrayList<Dependency> schemaDeps = filterDependencies();
            System.out.println("des: " + schemaDeps);
            unpack(schemaDeps);

            ValidatorBuilder builder = new ValidatorBuilder();
            builder.scanCatalogs(workingDir.toPath());

            ValidationErrorHandler errorHandler = builder.createErrorHandler();
            builder.setErrorHandler(errorHandler);

            ValidatorBuilder.XmlValidator validator = builder.build();

            List<File> validatingFiles = getValidatingFiles(getBaseDir(), resources.getIncludes(), resources.getExcludes());
            for (File file : validatingFiles) {
                validator.validate(file.toPath());
            }

            ErrorsSerializer serializer = new ErrorsSerializer();
            serializer.serialize(errorHandler);

        } catch (final Exception e) {
            throw new MojoExecutionException("validation failed", e);
        }
    }

    private void unpack(ArrayList<Dependency> schemaDeps) throws ArtifactResolverException, NoSuchArchiverException, IOException {
        for (Dependency schemaDep : schemaDeps) {
            unpackDependency(schemaDep, workingDir.toPath().resolve(schemaDep.getGroupId() + "-" + schemaDep.getArtifactId()).toFile());
        }
    }

    private ArrayList<Dependency> filterDependencies() {
        ArrayList<Dependency> included = new ArrayList<>();
        for (Dependency dependency : mavenProject.getDependencies()) {
            String dep = asString(dependency);

            if (schemas.getExcludes() != null) {
                for (String exclude : schemas.getExcludes()) {
                    if (dep.matches(exclude)) {
                        continue;
                    }

                }
            }
            if(schemas.getIncludes() != null){
                for (String include : schemas.getIncludes()) {
                    if(dep.matches(include)){
                        included.add(dependency);
                    }
                }
            }
            else {
                included.add(dependency);
            }
        }

        return included;
    }

    private String asString(Dependency dependency) {
        return new StringBuilder().append(dependency.getGroupId())
                .append(":")
                .append(dependency.getArtifactId())
                .append(":")
                .append(dependency.getVersion())
                .append(":")
                .append(dependency.getType())
                .append(":")
                .append(dependency.getClassifier())
                .toString();
    }

    private void unpackDependency(Dependency dependency, File where) throws IOException, NoSuchArchiverException, ArtifactResolverException {
        if (!Files.exists(where.toPath())) {
            Files.createDirectories(where.toPath());
        }

        Artifact artifact = getArtifact(dependency);

        UnArchiver unarch = archiverManager.getUnArchiver(artifact.getType());
        unarch.setDestDirectory(where);
        unarch.setSourceFile(artifact.getFile());
        unarch.extract();
    }


    private static List<File> getValidatingFiles(File baseDir, String[] includes, String[] excludes) throws IOException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(baseDir);
        if (includes != null) {
            ds.setIncludes(includes);
        }
        if (excludes != null) {
            ds.setExcludes(excludes);
        }
        ds.scan();
        logger.info("total files found: {}", Arrays.asList(ds.getIncludedFiles()));
        return asFiles(baseDir, ds.getIncludedFiles());
    }

    private Artifact getArtifact(Dependency dep) throws ArtifactResolverException {
        DefaultArtifactCoordinate coord = new DefaultArtifactCoordinate();

        coord.setGroupId(dep.getGroupId());
        coord.setVersion(dep.getVersion());
        coord.setExtension(dep.getType());
        coord.setArtifactId(dep.getArtifactId());
        coord.setClassifier(dep.getClassifier());

        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        List<ArtifactRepository> repoList = new ArrayList<ArtifactRepository>();
        repoList.addAll(pomRemoteRepositories);

        buildingRequest.setRemoteRepositories(repoList);
        buildingRequest.setLocalRepository(session.getLocalRepository());
        ArtifactResult result = artifactResolver.resolveArtifact(buildingRequest, coord);

        return result.getArtifact();

    }

    private static List<File> asFiles(File baseDir, String[] includedFiles) {
        return
                Stream.of(includedFiles).map(File::new).map(file -> file.isAbsolute() ? file : baseDir.toPath().resolve(file.toPath()).toFile())
                        .collect(Collectors.toList());
    }

}
