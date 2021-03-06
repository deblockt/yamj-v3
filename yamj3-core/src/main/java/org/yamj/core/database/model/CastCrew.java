/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.database.model;

import java.io.Serializable;
import javax.persistence.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.yamj.core.database.model.type.JobType;

@Entity
@Table(name = "cast_crew")
public class CastCrew implements Serializable {

    private static final long serialVersionUID = -3941301942248344131L;

    @EmbeddedId
    private CastCrewPK castCrewPK;

    @Column(name = "role", length = 255)
    private String role;

    @Column(name = "ordering", nullable = false)
    private int ordering;

    public CastCrew() {
    }

    public CastCrew(Person person, VideoData videoData, JobType jobType) {
        setCastCrewPK(new CastCrewPK(person, videoData, jobType));
    }

    // GETTER and SETTER
    public CastCrewPK getCastCrewPK() {
        return castCrewPK;
    }

    private void setCastCrewPK(CastCrewPK castCrewPK) {
        this.castCrewPK = castCrewPK;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getOrdering() {
        return ordering;
    }

    public void setOrdering(int ordering) {
        this.ordering = ordering;
    }

    // EQUALITY CHECKS
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(getCastCrewPK())
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CastCrew) {
            final CastCrew other = (CastCrew) obj;
            return new EqualsBuilder()
                    .append(getCastCrewPK(), other.getCastCrewPK())
                    .isEquals();
        }
        return false;
    }
}
