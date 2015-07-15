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
package org.yamj.core.service.metadata;

import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.interceptor.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableCaching
public class MetadataCachingConfiguration implements CachingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataCachingConfiguration.class);

    private static final String DEFAULT = "default";
    private static final String ALLOCINE_SEARCH = "allocineSearchCache";
    private static final String ALLOCINE_INFO = "allocineInfoCache";
    private static final String TVDB = "tvdbCache";
    
    @Bean(destroyMethod="shutdown")
    public net.sf.ehcache.CacheManager ehCacheManager() {
        return net.sf.ehcache.CacheManager.create(
            new net.sf.ehcache.config.Configuration()
                .defaultCache(cacheConfig(DEFAULT, 1000, 600, MemoryStoreEvictionPolicy.LRU))
                .cache(cacheConfig(ALLOCINE_SEARCH, 100, 300,  MemoryStoreEvictionPolicy.LFU))
                .cache(cacheConfig(ALLOCINE_INFO, 400, 1800,  MemoryStoreEvictionPolicy.LRU))
                .cache(cacheConfig(TVDB, 500, 1800,  MemoryStoreEvictionPolicy.LRU)));
    }

    @Scope
    @Bean
    @Override
    public CacheManager cacheManager() {
        LOG.trace("Create new cache manager using ehcache");
        return new EhCacheCacheManager(ehCacheManager());
    }

    @Override
    public KeyGenerator keyGenerator() {
        return new SimpleKeyGenerator();
    }

    @Override
    public CacheResolver cacheResolver() {
        return null;
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
    
    private static CacheConfiguration cacheConfig(String name, int maxEntries, long timeToLiveSeconds, MemoryStoreEvictionPolicy memoryStoreEvictionPolicy) {
        return new CacheConfiguration()
            .name(name)
            .eternal(false)
            .maxEntriesLocalHeap(maxEntries)
            .timeToIdleSeconds(0)
            .timeToLiveSeconds(timeToLiveSeconds)
            .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
            .memoryStoreEvictionPolicy(memoryStoreEvictionPolicy)
            .statistics(false);
    }

    @Bean
    public Cache allocineSearchCache() {
        return cacheManager().getCache(ALLOCINE_SEARCH);
    }

    @Bean
    public Cache allocineInfoCache() {
        return cacheManager().getCache(ALLOCINE_INFO);
    }

    @Bean
    public Cache tvdbCache() {
        return cacheManager().getCache(TVDB);
    }
}