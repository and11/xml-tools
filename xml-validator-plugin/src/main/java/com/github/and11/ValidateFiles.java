package com.github.and11;

import org.apache.maven.plugins.annotations.Parameter;

public class ValidateFiles {

    @Parameter
    private String[] includes;

    @Parameter
    private String[] excludes;

    public String[] getIncludes() {
        return includes;
    }

    public void setIncludes(String[] includes) {
        this.includes = includes;
    }

    public String[] getExcludes() {
        return excludes;
    }

    public void setExcludes(String[] excludes) {
        this.excludes = excludes;
    }
}
