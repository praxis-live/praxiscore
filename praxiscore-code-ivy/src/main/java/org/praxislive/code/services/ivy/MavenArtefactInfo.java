/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 * 
 *
 * Please visit https://www.praxislive.org if you need additional information or
 * have any questions.
 */
package org.praxislive.code.services.ivy;

import java.util.Objects;

/**
 *
 */
class MavenArtefactInfo {

    private final String group;
    private final String artefact;
    private final String version;
    private final String classifier;

    public MavenArtefactInfo(String group, String artefact, String version) {
        this(group, artefact, version, "");
    }
    
    public MavenArtefactInfo(String group, String artefact, String version, String classifier) {
        this.group = group;
        this.artefact = artefact;
        this.version = version;
        this.classifier = classifier;
    }

    public String group() {
        return group;
    }

    public String artefact() {
        return artefact;
    }

    public String version() {
        return version;
    }

    public String classifier() {
        return classifier;
    }
    
    public boolean isMatchingArtefact(MavenArtefactInfo info) {
        return Objects.equals(group, info.group())
                && Objects.equals(artefact, info.artefact())
                && Objects.equals(classifier, info.classifier());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.group);
        hash = 97 * hash + Objects.hashCode(this.artefact);
        hash = 97 * hash + Objects.hashCode(this.version);
        hash = 97 * hash + Objects.hashCode(this.classifier);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MavenArtefactInfo other = (MavenArtefactInfo) obj;
        if (!Objects.equals(this.group, other.group)) {
            return false;
        }
        if (!Objects.equals(this.artefact, other.artefact)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        if (!Objects.equals(this.classifier, other.classifier)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "MavenArtefactInfo{" + "group=" + group + ","
                + "artefact=" + artefact + ","
                + "version=" + version + ","
                + "classifier=" + classifier + '}';
    }

    
    
}
