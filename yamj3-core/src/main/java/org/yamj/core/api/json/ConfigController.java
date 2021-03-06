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
package org.yamj.core.api.json;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.yamj.core.api.model.ApiStatus;
import org.yamj.core.api.options.OptionsConfig;
import org.yamj.core.api.wrapper.ApiWrapperList;
import org.yamj.core.config.ConfigService;
import org.yamj.core.database.model.Configuration;

@Controller
@ResponseBody
@RequestMapping(value = "/api/config/**", method = RequestMethod.GET, produces = "application/json; charset=utf-8")
public class ConfigController {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigController.class);
    @Autowired
    private ConfigService configService;

    @RequestMapping("/list")
    public ApiWrapperList<Configuration> configList(@ModelAttribute("options") OptionsConfig options) {
        if (StringUtils.isBlank(options.getConfig())) {
            LOG.info("Getting all configuration entries");
        } else {
            LOG.info("Getting configuration properties for '{}'", options.getConfig());
        }
        ApiWrapperList<Configuration> wrapper = new ApiWrapperList<>();

        // If not mode is specified, make it exact
        if (StringUtils.isBlank(options.getMode())) {
            options.setMode("EXACT");
        }
        wrapper.setOptions(options);
        wrapper.setResults(configService.getConfiguration(options));
        wrapper.setStatusCheck();

        return wrapper;
    }

    @RequestMapping("/add")
    public ApiStatus configAdd(
            @RequestParam(required = true, defaultValue = "") String key,
            @RequestParam(required = true, defaultValue = "") String value) {

        ApiStatus status = new ApiStatus();
        if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
            LOG.info("Storing config '{}' with value '{}'", key, value);
            configService.setProperty(key, value);
            status.setStatus(200);
            status.setMessage("Successfully added '" + key + "' with value '" + value + "'");
        } else {
            status.setStatus(400);
            status.setMessage("Invalid key/value specified, configuration not added");
        }
        return status;
    }

    @RequestMapping("/delete")
    public ApiStatus configDelete(
            @RequestParam(required = true, defaultValue = "") String key) {
        ApiStatus status = new ApiStatus();
        if (StringUtils.isNotBlank(key)) {
            LOG.info("Deleting config '{}'", key);
            configService.deleteProperty(key);
            status.setStatus(200);
            status.setMessage("Successfully deleted '" + key + "'");
        } else {
            status.setStatus(400);
            status.setMessage("Invalid key specified, configuration not deleted");
        }
        return status;
    }

    @RequestMapping("/update")
    public ApiStatus configUpdate(
            @RequestParam(required = true, defaultValue = "") String key,
            @RequestParam(required = true, defaultValue = "") String value) {
        ApiStatus status = new ApiStatus();
        if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
            LOG.info("Updating config '{}' with value '{}'", key, value);
            configService.setProperty(key, value);
            status.setStatus(200);
            status.setMessage("Successfully updated '" + key + "' to value '" + value + "'");
        } else {
            status.setStatus(400);
            status.setMessage("Invalid key/value specified, configuration not updated");
        }
        return status;
    }

}
