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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "validate", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.TEST)
public class ValidatorMojo extends AbstractMojo {

    public static final Logger logger = LoggerFactory.getLogger(ValidatorMojo.class);

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private MavenProject mavenProject;

    @Parameter
    private File unpackDirectory;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    @Parameter( property = "xml.skip", defaultValue = "false" )
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
    private String[] includes;

    @Parameter
    private String[] excludes;

    public File getBaseDir() {
        return baseDir;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if(skip){
            logger.info("xml validation skipped by user (xml.stip property is true)");
            return;
        }

        try {
            Optional<String> version = calculateSchemaVersion();
            if(!version.isPresent()){
                throw new MojoExecutionException("can't get xml schema version");
            }

            Artifact artifact = getCatalogArtifact(version.get());
            File unpackDir = getUnpackDirectory(artifact);
            unpackCatalog(artifact, unpackDir);

            ValidatorBuilder builder = new ValidatorBuilder();
            builder.addCatalogs(Arrays.asList(unpackDir.toPath().resolve("catalog.xml")));

            ValidationErrorHandler errorHandler = builder.createErrorHandler();
            builder.setErrorHandler(errorHandler);

            ValidatorBuilder.XmlValidator validator = builder.build();

            List<File> validatingFiles = getValidatingFiles(getBaseDir(), includes, excludes);
            for (File file : validatingFiles) {
                validator.validate(file.toPath());
            }

            ErrorsSerializer serializer = new ErrorsSerializer();
            serializer.serialize(errorHandler);

        } catch (final Exception e) {
            throw new MojoExecutionException("validation failed", e);
        }
    }

    private Optional<String> getSchemaVersionFromDependencies() {
        return mavenProject.getDependencies().stream()
                .filter(d -> "com.openapi.doc.schemas".equals(d.getGroupId()))
                .filter(d -> "xsd".equals(d.getArtifactId()) || "catalog".equals(d.getArtifactId()))
                .map(Dependency::getVersion)
                .findFirst();
    }

    private Artifact getCatalogArtifact(String version) throws ArtifactResolverException {
        DefaultArtifactCoordinate coord = new DefaultArtifactCoordinate();

        coord.setGroupId("com.peterservice.openapi.doc.schemas");
        coord.setVersion(version);
        coord.setExtension("zip");
        coord.setArtifactId("catalog");

        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        List<ArtifactRepository> repoList = new ArrayList<ArtifactRepository>();
        repoList.addAll(pomRemoteRepositories);

        buildingRequest.setRemoteRepositories(repoList);
        buildingRequest.setLocalRepository(session.getLocalRepository());
        ArtifactResult result = artifactResolver.resolveArtifact(buildingRequest, coord);

        return result.getArtifact();

    }

    private void unpackCatalog(Artifact artifact, File where) throws IOException, NoSuchArchiverException {
        if (!Files.exists(where.toPath())) {
            Files.createDirectories(where.toPath());
        }

        UnArchiver unarch = archiverManager.getUnArchiver("zip");
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
//        return Files.walk(new File(mavenProject.getBuild().getOutputDirectory()).toPath())
//                .filter(Files::isRegularFile)
//                .map(Path::toFile).collect(Collectors.toList());
    }

    private static List<File> asFiles(File baseDir, String[] includedFiles) {
        return
                Stream.of(includedFiles).map(File::new).map(file -> file.isAbsolute() ? file : baseDir.toPath().resolve(file.toPath()).toFile())
                        .collect(Collectors.toList());
    }

    private File getUnpackDirectory(Artifact artifact) {
        return unpackDirectory != null ?
                unpackDirectory :
                new File(mavenProject.getBuild().getDirectory()).toPath()
                        .resolve("schemas")
                        .resolve(artifact.getArtifactId() + "-" + artifact.getVersion())
                        .toFile();
    }

    private Optional<String> getExplicitSchemaVersion() {
        return schemaVersion == null ? Optional.empty() : Optional.of(schemaVersion);
    }

    private Optional<String> calculateSchemaVersion() {
        return getExplicitSchemaVersion().isPresent() ? getExplicitSchemaVersion() : getSchemaVersionFromDependencies();
    }

}
