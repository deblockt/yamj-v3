/*
 *      Copyright (c) 2004-2014 YAMJ Members
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
package org.yamj.core.service.mediaimport;

import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yamj.common.type.StatusType;
import org.yamj.core.configuration.ConfigServiceWrapper;
import org.yamj.core.database.dao.MediaDao;
import org.yamj.core.database.dao.MetadataDao;
import org.yamj.core.database.dao.StagingDao;
import org.yamj.core.database.model.*;
import org.yamj.core.database.model.type.ArtworkType;
import org.yamj.core.database.model.type.FileType;
import org.yamj.core.database.service.CommonStorageService;
import org.yamj.core.database.service.MetadataStorageService;
import org.yamj.core.service.file.tools.FileTools;
import org.yamj.core.service.mediaimport.FilenameDTO.SetDTO;
import org.yamj.core.service.metadata.tools.MetadataTools;

/**
 * The media import service is a spring-managed service. This will be used by the MediaImportRunner only in order to access other
 * spring beans cause the MediaImportRunner itself is no spring-managed bean and dependency injection will fail on that runner.
 *
 */
@Service("mediaImportService")
public class MediaImportService {

    private static final Logger LOG = LoggerFactory.getLogger(MediaImportService.class);
    private static final String MEDIA_SOURCE = "filename";
    private static final String TVSHOW_NFO_NAME = "tvshow";
    private static final String BDMV_FOLDER = "BDMV";
    private static final String DVD_FOLDER = "VIDEO_TS";
    
    @Autowired
    private StagingDao stagingDao;
    @Autowired
    private MediaDao mediaDao;
    @Autowired
    private MetadataDao metadataDao;
    @Autowired
    private FilenameScanner filenameScanner;
    @Autowired
    private ConfigServiceWrapper configServiceWrapper;
    @Autowired
    private MetadataStorageService metadataStorageService;
    @Autowired
    private CommonStorageService commonStorageService;
    
    @Transactional(readOnly = true)
    public Long getNextStageFileId(final FileType fileType, final StatusType... statusTypes) {
        return this.stagingDao.getNextStageFileId(fileType, statusTypes);
    }

    @Transactional
    public void processVideo(long id) {
        StageFile stageFile = stagingDao.getStageFile(id);
        
        if (stageFile.getMediaFile() == null) {
            LOG.info("Process new video {}-'{}'", stageFile.getId(), stageFile.getFileName());
            
            // process new video
            processNewVideo(stageFile);

            // attach NFO files
            attachNfoFilesToVideo(stageFile);
            
            // TODO attach subtitles
        } else {
            LOG.info("Process updated video {}-'{}'", stageFile.getId(), stageFile.getFileName());
            
            // just update media file
            MediaFile mediaFile = stageFile.getMediaFile();
            mediaFile.setStatus(StatusType.UPDATED);
            mediaDao.updateEntity(mediaFile);
        }

        // mark stage file as done
        stageFile.setStatus(StatusType.DONE);
        stagingDao.updateEntity(stageFile);
    }

    private void processNewVideo(StageFile stageFile) {
        
        // check if same media file already exists
        MediaFile mediaFile = mediaDao.getMediaFile(stageFile.getFileName());
        if (mediaFile != null) {
            LOG.warn("Media file for '{}' already present for new stage file", stageFile.getFileName());

            mediaFile.addStageFile(stageFile);
            stageFile.setMediaFile(mediaFile);
            stageFile.setStatus(StatusType.DUPLICATE);
            mediaDao.updateEntity(mediaFile);
            return;
        }

        // scan filename for informations
        FilenameDTO dto = new FilenameDTO(stageFile);
        filenameScanner.scan(dto);
        LOG.debug("Scanned filename {}-'{}': title='{}', year={}",
                stageFile.getId(), stageFile.getFileName(), dto.getTitle(), dto.getYear());

        if (StringUtils.isBlank(dto.getTitle()) && (dto.getYear() > 0)) {
            if (dto.getYear() > 0) {
                LOG.warn("No valid title scanned from '{}', year will be used as title", stageFile.getFileName());
                dto.setTitle(String.valueOf(dto.getYear()));
                dto.setYear(-1);
            } else {
                LOG.error("No valid title and year could be scanned from filename '{}'", stageFile.getFileName());
                stageFile.setStatus(StatusType.ERROR);
                mediaDao.updateEntity(stageFile);
                return;
            }
        }
        
        // check for watched file
        StageFile watchFile = this.stagingDao.getStageFile(FileType.WATCHED, stageFile.getFileName(), stageFile.getStageDirectory());
        
        // new media file
        mediaFile = new MediaFile();
        mediaFile.setFileName(stageFile.getFileName());
        mediaFile.setContainer(dto.getContainer());
        mediaFile.setExtra(dto.isExtra());
        mediaFile.setPart(dto.getPart());
        mediaFile.setPartTitle(dto.getPartTitle());
        mediaFile.setMovieVersion(dto.getMovieVersion());
        mediaFile.setFps(dto.getFps());
        mediaFile.setCodec(dto.getVideoCodec());
        mediaFile.setVideoSource(dto.getVideoSource());
        mediaFile.setEpisodeCount(dto.getEpisodes().size());
        mediaFile.setStatus(StatusType.NEW);
        mediaFile.setWatchedFile((watchFile!=null));
        mediaFile.addStageFile(stageFile);
        stageFile.setMediaFile(mediaFile);

        LOG.debug("Store new media file: '{}'", mediaFile.getFileName());
        mediaDao.saveEntity(mediaFile);
        
        // METADATA OBJECTS
                
        if (dto.isMovie()) {
            // VIDEO DATA for movies

            String identifier = dto.buildIdentifier();
            VideoData videoData = metadataDao.getVideoData(identifier);
            if (videoData == null) {

                // NEW video data
                videoData = new VideoData();
                videoData.setIdentifier(identifier);
                videoData.setSourceDbIdMap(dto.getIdMap());
                videoData.setTitle(dto.getTitle(), MEDIA_SOURCE);
                videoData.setTitleOriginal(dto.getTitle(), MEDIA_SOURCE);
                videoData.setPublicationYear(dto.getYear(), MEDIA_SOURCE);
                videoData.setStatus(StatusType.NEW);
                mediaFile.addVideoData(videoData);
                videoData.addMediaFile(mediaFile);

                // set watched if media file is NO extra
                if (!mediaFile.isExtra()) {
                    videoData.setWatchedFile(mediaFile.isWatchedFile());
                }
                
                LOG.debug("Store new movie: '{}' - {}", videoData.getTitle(), videoData.getPublicationYear());
                metadataDao.saveEntity(videoData);

                // create new poster artwork entry
                Artwork poster = new Artwork();
                poster.setArtworkType(ArtworkType.POSTER);
                poster.setStatus(StatusType.NEW);
                poster.setVideoData(videoData);
                metadataDao.saveEntity(poster);

                // create new fanart artwork entry
                Artwork fanart = new Artwork();
                fanart.setArtworkType(ArtworkType.FANART);
                fanart.setStatus(StatusType.NEW);
                fanart.setVideoData(videoData);
                metadataDao.saveEntity(fanart);

            } else {
                mediaFile.addVideoData(videoData);
                videoData.addMediaFile(mediaFile);
                
                // set watched file if all media files are watched
                boolean watchedFile = MetadataTools.allMediaFilesWatched(videoData, false);
                videoData.setWatchedFile(watchedFile);

                // set watched ap if all media files are watched
                boolean watchedApi = MetadataTools.allMediaFilesWatched(videoData, true);
                videoData.setWatchedApi(watchedApi);

                metadataDao.updateEntity(videoData);
            }

            // boxed set handling
            if (CollectionUtils.isNotEmpty(dto.getSets())) {
                for (SetDTO set: dto.getSets()) {
                    // add boxed set to video data
                    videoData.addSetInfo(set.getTitle(), set.getIndex());
                }

                // store associated entities (only sets right now)
                this.metadataStorageService.storeAssociatedEntities(videoData);
                // updated boxed sets for video data
                this.metadataStorageService.updateBoxedSets(videoData);
            }

        } else {
            // VIDEO DATA for episodes
            for (Integer episode : dto.getEpisodes()) {
                String identifier = dto.buildEpisodeIdentifier(episode);
                VideoData videoData = metadataDao.getVideoData(identifier);
                if (videoData == null) {
                    // NEW video data

                    // getById or create season
                    String seasonIdentifier = dto.buildSeasonIdentifier();
                    Season season = metadataDao.getSeason(seasonIdentifier);
                    if (season == null) {

                        // getById or create series
                        String seriesIdentifier = dto.buildIdentifier();
                        Series series = metadataDao.getSeries(seriesIdentifier);
                        if (series == null) {
                            series = new Series();
                            series.setIdentifier(seriesIdentifier);
                            series.setTitle(dto.getTitle(), MEDIA_SOURCE);
                            series.setTitleOriginal(dto.getTitle(), MEDIA_SOURCE);
                            series.setSourceDbIdMap(dto.getIdMap());
                            series.setStatus(StatusType.NEW);
                            LOG.debug("Store new series: '{}'", series.getTitle());
                            metadataDao.saveEntity(series);

                            // create new poster artwork entry
                            Artwork poster = new Artwork();
                            poster.setArtworkType(ArtworkType.POSTER);
                            poster.setStatus(StatusType.NEW);
                            poster.setSeries(series);
                            metadataDao.saveEntity(poster);

                            // create new fanart artwork entry
                            Artwork fanart = new Artwork();
                            fanart.setArtworkType(ArtworkType.FANART);
                            fanart.setStatus(StatusType.NEW);
                            fanart.setSeries(series);
                            metadataDao.saveEntity(fanart);

                            // create new banner artwork entry
                            Artwork banner = new Artwork();
                            banner.setArtworkType(ArtworkType.BANNER);
                            banner.setStatus(StatusType.NEW);
                            banner.setSeries(series);
                            metadataDao.saveEntity(banner);
                        }

                        season = new Season();
                        season.setIdentifier(seasonIdentifier);
                        season.setSeason(dto.getSeason());
                        season.setTitle(dto.getTitle(), MEDIA_SOURCE);
                        season.setTitleOriginal(dto.getTitle(), MEDIA_SOURCE);
                        season.setSeries(series);
                        season.setStatus(StatusType.NEW);
                        
                        LOG.debug("Store new seaon: '{}' - Season {}", season.getTitle(), season.getSeason());
                        metadataDao.saveEntity(season);

                        // create new poster artwork entry
                        Artwork poster = new Artwork();
                        poster.setArtworkType(ArtworkType.POSTER);
                        poster.setStatus(StatusType.NEW);
                        poster.setSeason(season);
                        metadataDao.saveEntity(poster);

                        // create new fanart artwork entry
                        Artwork fanart = new Artwork();
                        fanart.setArtworkType(ArtworkType.FANART);
                        fanart.setStatus(StatusType.NEW);
                        fanart.setSeason(season);
                        metadataDao.saveEntity(fanart);

                        // create new banner artwork entry
                        Artwork banner = new Artwork();
                        banner.setArtworkType(ArtworkType.BANNER);
                        banner.setStatus(StatusType.NEW);
                        banner.setSeason(season);
                        metadataDao.saveEntity(banner);
                    }

                    videoData = new VideoData();
                    videoData.setIdentifier(identifier);
                    if (StringUtils.isNotBlank(dto.getEpisodeTitle())) {
                        videoData.setTitle(dto.getEpisodeTitle(), MEDIA_SOURCE);
                        videoData.setTitleOriginal(dto.getEpisodeTitle(), MEDIA_SOURCE);
                    } else {
                        videoData.setTitle(dto.getTitle(), MEDIA_SOURCE);
                        videoData.setTitleOriginal(dto.getTitle(), MEDIA_SOURCE);
                    }
                    videoData.setStatus(StatusType.NEW);
                    videoData.setSeason(season);
                    videoData.setEpisode(episode);
                    mediaFile.addVideoData(videoData);
                    videoData.addMediaFile(mediaFile);

                    LOG.debug("Store new episode: '{}' - Season {} - Episode {}", season.getTitle(), season.getSeason(), videoData.getEpisode());
                    metadataDao.saveEntity(videoData);

                    // create new videoimage artwork entry
                    Artwork videoimage = new Artwork();
                    videoimage.setArtworkType(ArtworkType.VIDEOIMAGE);
                    videoimage.setStatus(StatusType.NEW);
                    videoimage.setVideoData(videoData);
                    metadataDao.saveEntity(videoimage);

                } else {
                    mediaFile.addVideoData(videoData);
                    videoData.addMediaFile(mediaFile);
                    metadataDao.updateEntity(videoData);
                }
            }
        }
    }
    
    private void attachNfoFilesToVideo(StageFile stageFile) {
        if (stageFile.getMediaFile() == null) {
            // video file must be associated to a media file 
            return;
        }
        if (stageFile.getMediaFile().isExtra()) {
            // media file may not be an extra
            return;
        }
        Set<VideoData> videoDatas = stageFile.getMediaFile().getVideoDatas();
        if (CollectionUtils.isEmpty(videoDatas)) {
            // videos must exists
            return;
        }

        // evaluate if TV show
        boolean isTvShow = false;
        for (VideoData videoData : videoDatas) {
            if (!videoData.isMovie()) {
                isTvShow = true;
                break;
            }
        }

        // holds the found NFO files with priority
        Map<StageFile, Integer> nfoFiles = new HashMap<StageFile,Integer>();

        // search name is the base name of the stage file
        String searchName = stageFile.getBaseName();
        
        // BDMV and VIDEO_TS folder handling
        StageDirectory directory = stageFile.getStageDirectory();
        if (this.isBlurayOrDvdFolder(directory)) {
            // use parent directory for search
            directory = directory.getParentDirectory();
            // search for name of parent directory
            searchName = directory.getDirectoryName();
        }
        
        // case 1: find matching NFO in directory
        StageFile foundNfoFile = this.stagingDao.findNfoFile(searchName, directory);
        if (foundNfoFile != null && !nfoFiles.containsKey(foundNfoFile)) {
            nfoFiles.put(foundNfoFile, Integer.valueOf(1));
            
            // change status for PRIO-1-NFO
            if (FileTools.isFileScannable(foundNfoFile)) {
                foundNfoFile.setStatus(StatusType.DONE);
            } else {
                foundNfoFile.setStatus(StatusType.INVALID);
            }
            this.stagingDao.updateEntity(stageFile);
        }

        if (isTvShow) {
            // case 2: tvshow.nfo in same directory as video
            foundNfoFile = this.stagingDao.findNfoFile(TVSHOW_NFO_NAME, directory);
            if (foundNfoFile != null && !nfoFiles.containsKey(foundNfoFile)) {
                nfoFiles.put(foundNfoFile, Integer.valueOf(2));
            }

            // case 3: tvshow.nfo in parent directory
            foundNfoFile = this.stagingDao.findNfoFile(TVSHOW_NFO_NAME, directory.getParentDirectory());
            if (foundNfoFile != null && !nfoFiles.containsKey(foundNfoFile)) {
                nfoFiles.put(foundNfoFile, Integer.valueOf(3));
            }
        }
        
        // case 10-n: apply "nfoName = dirName" to all video data
        // NOTE: 11-n are only applied if recursive scan is enabled
        boolean recurse = this.configServiceWrapper.getBooleanProperty("yamj3.scan.nfo.recursiveDirectories", false);
        LOG.trace("Recursive scan of directories is {}", (recurse?"enabled":"disabled"));
        this.findNfoWithDirectoryName(nfoFiles, stageFile.getStageDirectory(), 10, recurse);
        
        if (MapUtils.isEmpty(nfoFiles)) {
            // no NFO files found
            return;
        }

        for (Entry<StageFile,Integer> entry : nfoFiles.entrySet()) {
            StageFile nfoFile = entry.getKey();
            int priority = entry.getValue().intValue();
            
            for (VideoData videoData : videoDatas) {
                LOG.debug("Found NFO {}-'{}' with priority {} for video data '{}'",
                                nfoFile.getId(), nfoFile.getFileName(), priority, videoData.getIdentifier());

                NfoRelation nfoRelation = new NfoRelation();
                nfoRelation.setStageFile(nfoFile);
                nfoRelation.setVideoData(videoData);
                nfoRelation.setPriority(priority);

                if (!videoData.getNfoRelations().contains(nfoRelation)) {
                    this.mediaDao.saveEntity(nfoRelation);
                    videoData.addNfoRelation(nfoRelation);
                    nfoFile.addNfoRelation(nfoRelation);

                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Stored new NFO relation: stageFile={}, videoData={}",
                                        nfoRelation.getStageFile().getId(),
                                        nfoRelation.getVideoData().getId());
                    }
                }
            }
        }
    }

    private void findNfoWithDirectoryName(Map<StageFile,Integer> nfoFiles, StageDirectory directory, int counter, boolean recurse) {
        if (directory == null) return;
        
        StageFile foundNfoFile = this.stagingDao.findNfoFile(directory.getDirectoryName(), directory);
        if (foundNfoFile != null && !nfoFiles.containsKey(foundNfoFile)) {
            nfoFiles.put(foundNfoFile, Integer.valueOf(counter));
        }
        
        if (recurse) {
            // recurse until parent is null
            this.findNfoWithDirectoryName(nfoFiles, directory.getParentDirectory(), (counter + 1), recurse);
        }
    }
    
    private boolean isBlurayOrDvdFolder(StageDirectory directory) {
        if (directory == null) {
            return false;
        }
        if (BDMV_FOLDER.equalsIgnoreCase(directory.getDirectoryName())) {
            return true;
        }
        if (DVD_FOLDER.equalsIgnoreCase(directory.getDirectoryName())) {
            return true;
        }
        return false;
    }
    
    @Transactional
    public void processingError(Long id) {
        if (id == null) {
            return;
        }

        StageFile stageFile = stagingDao.getStageFile(id);
        if (stageFile != null) {
            stageFile.setStatus(StatusType.ERROR);
            stagingDao.updateEntity(stageFile);
        }
    }

    @Transactional
    public void processNfo(long id) {
        StageFile stageFile = stagingDao.getStageFile(id);
        if (StatusType.NEW.equals(stageFile.getStatus())) {
            LOG.info("Process new nfo {}-'{}'", stageFile.getId(), stageFile.getFileName());
            
            // process new NFO
            processNewNFO(stageFile);
        } else {
            LOG.info("Process updated nfo {}-'{}'", stageFile.getId(), stageFile.getFileName());
        }

        // check if NFO file can be scanned
        if (!FileTools.isFileScannable(stageFile)) {
            LOG.debug("NFO file {}-'{}' is not scannable", stageFile.getId(), stageFile.getFileName());
            stageFile.setStatus(StatusType.INVALID);
            stagingDao.updateEntity(stageFile);
            // nothing to do anymore
            return;
        }
        
        // mark stage file as done
        stageFile.setStatus(StatusType.DONE);
        stagingDao.updateEntity(stageFile);        
        
        // update meta-data for NFO scan
        for (NfoRelation nfoRelation : stageFile.getNfoRelations()) {
            VideoData videoData = nfoRelation.getVideoData();
            if (videoData.isMovie()) {
                videoData.setStatus(StatusType.UPDATED);
                stagingDao.updateEntity(videoData);

                LOG.debug("Marked movied {}-'{}' as updated", videoData.getId(), videoData.getTitle());
            } else {
                videoData.setStatus(StatusType.UPDATED);
                stagingDao.updateEntity(videoData);

                Season season = videoData.getSeason();
                season.setStatus(StatusType.UPDATED);
                stagingDao.updateEntity(videoData);
                
                Series series = season.getSeries();
                series.setStatus(StatusType.UPDATED);
                stagingDao.updateEntity(series);
                
                LOG.debug("Marked series {}-'{}' as updated", series.getId(), series.getTitle());
            }
        }
    }

    private void processNewNFO(StageFile stageFile) {
        // find video files for this NFO file
        Map<VideoData,Integer> videoFiles = this.findVideoFilesForNFO(stageFile);
        if (MapUtils.isEmpty(videoFiles)) {
            // no NFO files found
            return;
        }

        for (Entry<VideoData,Integer> entry : videoFiles.entrySet()) {
            VideoData videoData = entry.getKey();
            int priority = entry.getValue().intValue();
            
            LOG.debug("Found video data {}-'{}' for nfo file '{}' with priority {}",
                            videoData.getId(), videoData.getIdentifier(), stageFile.getFileName(), priority);

            NfoRelation nfoRelation = new NfoRelation();
            nfoRelation.setStageFile(stageFile);
            nfoRelation.setVideoData(videoData);
            nfoRelation.setPriority(priority);

            if (!stageFile.getNfoRelations().contains(nfoRelation)) {
                this.mediaDao.saveEntity(nfoRelation);
                stageFile.addNfoRelation(nfoRelation);
                videoData.addNfoRelation(nfoRelation);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Stored new NFO relation: stageFile={}, videoData={}",
                                    nfoRelation.getStageFile().getId(),
                                    nfoRelation.getVideoData().getId());
                }
            }
        }
    }
    
    private Map<VideoData,Integer> findVideoFilesForNFO(StageFile stageFile) {
        Map<VideoData,Integer> videoFiles = new HashMap<VideoData,Integer>();
        List<VideoData> videoDatas = null;
        
        if (stageFile.getBaseName().equalsIgnoreCase(stageFile.getStageDirectory().getDirectoryName())) {
            if (isBlurayOrDvdFolder(stageFile.getStageDirectory())) {
                // ignore NFO in BDMV or DVD folder; should be placed in parent directory 
                return videoFiles;
            }
            
            // case 10: apply to all video data in stage directory
            videoDatas = this.stagingDao.findVideoDatas(stageFile.getStageDirectory());
            this.attachVideoDataToNFO(videoFiles, videoDatas, 10);
            
            // get child directories
            List<StageDirectory> childDirectories = this.stagingDao.getChildDirectories(stageFile.getStageDirectory());
            if (CollectionUtils.isEmpty(childDirectories)) {
                return videoFiles;
            }
                
            // filter out bluray and DVD folders from child directories
            List<StageDirectory> blurayOrDvdFolders = new ArrayList<StageDirectory>();
            for (StageDirectory directory : childDirectories) {
                if (this.isBlurayOrDvdFolder(directory)) {
                    blurayOrDvdFolders.add(directory);
                }
            }
            childDirectories.removeAll(blurayOrDvdFolders);

            // case 1: bluray/DVD handling
            if (CollectionUtils.isNotEmpty(blurayOrDvdFolders)) {
                for (StageDirectory folder : blurayOrDvdFolders) {
                    videoDatas = this.stagingDao.findVideoDatas(folder);
                    this.attachVideoDataToNFO(videoFiles, videoDatas, 1);
                }
            }
            
            
            if (CollectionUtils.isNotEmpty(childDirectories)) {
                boolean recurse = this.configServiceWrapper.getBooleanProperty("yamj3.scan.nfo.recursiveDirectories", false);
                LOG.trace("Recursive scan of directories is {}", (recurse?"enabled":"disabled"));
                
                if (recurse) {
                    // // case 11-n: recursive scanning
                }
            }
            
        } else if (TVSHOW_NFO_NAME.equals(stageFile.getBaseName())) {
            // case 2: tvshow.nfo in same directory as video
            videoDatas = this.stagingDao.findVideoDatas(stageFile.getStageDirectory());
            this.attachVideoDataToNFO(videoFiles, videoDatas, 2, true);
            
            // case 3: tvshow.nfo in parent directory (so search in child directories)
            List<StageDirectory> childDirectories = this.stagingDao.getChildDirectories(stageFile.getStageDirectory());
            videoDatas = this.stagingDao.findVideoDatas(childDirectories);
            this.attachVideoDataToNFO(videoFiles, videoDatas, 3, true);
        } else {
            // case 1: Video file has same base name in same directory
            videoDatas = this.stagingDao.findVideoDatas(stageFile.getBaseName(), stageFile.getStageDirectory());
            this.attachVideoDataToNFO(videoFiles, videoDatas, 1);
        }
        
        return videoFiles;
    }

    private void attachVideoDataToNFO(Map<VideoData,Integer> videoFiles, Collection<VideoData> videoDatas, int priority) {
        this.attachVideoDataToNFO(videoFiles, videoDatas, priority, false);
    }

    private void attachVideoDataToNFO(Map<VideoData,Integer> videoFiles, Collection<VideoData> videoDatas, int priority, boolean tvShowOnly) {
        if (CollectionUtils.isNotEmpty(videoDatas)) {
            for (VideoData videoData : videoDatas) {
                if (tvShowOnly && videoData.isMovie()) {
                    // do not apply
                } else {
                    videoFiles.put(videoData, Integer.valueOf(priority));
                }
            }
        }
    }
    
    @Transactional
    public void processImage(long id) {
        StageFile stageFile = stagingDao.getStageFile(id);
        boolean found;
        if (StatusType.NEW.equals(stageFile.getStatus())) {
            LOG.info("Process new image {}-'{}'", stageFile.getId(), stageFile.getFileName());
            
            // process new image
            found = processNewImage(stageFile);
        } else {
            LOG.info("Process updated image {}-'{}'", stageFile.getId(), stageFile.getFileName());
            found = Boolean.TRUE;
            
            for (ArtworkLocated located : stageFile.getArtworkLocated()) {
                // mark located as updated
                located.setStatus(StatusType.UPDATED);
                this.mediaDao.updateEntity(located);
            }
        }

        // update stage file
        if (found) {
            stageFile.setStatus(StatusType.DONE);
        }  else {
            stageFile.setStatus(StatusType.NOTFOUND);
        }
        stagingDao.updateEntity(stageFile);
    }
    
    private boolean processNewImage(StageFile stageFile) {
        List<Artwork> artworks;
        if (stageFile.getBaseName().equalsIgnoreCase("poster")
                || stageFile.getBaseName().equalsIgnoreCase("cover")
                || stageFile.getBaseName().equalsIgnoreCase("folder")) 
        {
            LOG.debug("Generic poster found: {} in {}", stageFile.getBaseName(), stageFile.getStageDirectory().getDirectoryName());
            artworks = this.stagingDao.findMatchingArtworksForVideo(ArtworkType.POSTER, stageFile.getStageDirectory());
        }
        else if (stageFile.getBaseName().equalsIgnoreCase("fanart")
                   || stageFile.getBaseName().equalsIgnoreCase("backdrop")
                   || stageFile.getBaseName().equalsIgnoreCase("background")) 
        {
            LOG.debug("Generic fanart found: {} in {}", stageFile.getBaseName(), stageFile.getStageDirectory().getDirectoryName());
            artworks = this.stagingDao.findMatchingArtworksForVideo(ArtworkType.FANART, stageFile.getStageDirectory());
        }
        else if (StringUtils.endsWithIgnoreCase(stageFile.getBaseName(), ".fanart")
                 || StringUtils.endsWithIgnoreCase(stageFile.getBaseName(), "-fanart")) 
        {
            LOG.debug("Fanart found: {}", stageFile.getBaseName());

            String stripped = stageFile.getBaseName().toLowerCase();
            stripped = StringUtils.substring(stripped, 0, stripped.length()-7);
            
            if (stageFile.getStageDirectory().getDirectoryName().equalsIgnoreCase(stripped)) {
                // fanart for whole directory cause same name as directory
                artworks = this.stagingDao.findMatchingArtworksForVideo(ArtworkType.FANART, stageFile.getStageDirectory());
            } else {
                artworks = this.stagingDao.findMatchingArtworksForVideo(ArtworkType.FANART, stripped, stageFile.getStageDirectory());
            }
        }
        else if (StringUtils.indexOf(stageFile.getBaseName(), ".videoimage") > 0) 
        {
            LOG.debug("VideoImage found: {}", stageFile.getBaseName());

            // TODO apply episode image (which may be for just a part) 
            artworks = Collections.emptyList();
        }
        else 
        {
            LOG.debug("Poster found: {}", stageFile.getBaseName());

            String stripped = stageFile.getBaseName().toLowerCase();
            if (StringUtils.endsWith(stripped, ".poster")
                || StringUtils.endsWith(stripped, "-poster")) 
            {
                stripped = StringUtils.substring(stripped, 0, stripped.length()-7);
            }

            if (stageFile.getStageDirectory().getDirectoryName().equalsIgnoreCase(stripped)) {
                // poster for whole directory cause same name as directory
                artworks = this.stagingDao.findMatchingArtworksForVideo(ArtworkType.POSTER, stageFile.getStageDirectory());
            } else {
                artworks = this.stagingDao.findMatchingArtworksForVideo(ArtworkType.POSTER, stripped, stageFile.getStageDirectory());
            }
        }

        LOG.debug("Found {} matching artwork entries", artworks.size());
        if (CollectionUtils.isEmpty(artworks)) {
            // no artworks found so return
            return false;
        }
        
        // add artwork stage file to artwork
        for (Artwork artwork : artworks) {
            if (!configServiceWrapper.isLocalArtworkScanEnabled(artwork)) {
                LOG.trace("Local artwork scan disabled: {}", artwork);
                continue;
            }

            ArtworkLocated located = new ArtworkLocated();
            located.setArtwork(artwork);
            located.setSource("file");
            located.setPriority(1);
            located.setStageFile(stageFile);

            if (!artwork.getArtworkLocated().contains(located)) {
                int hash = stageFile.getFullPath().hashCode();
                located.setHashCode(String.valueOf((hash < 0 ? 0 - hash : hash)));

                if (FileTools.isFileReadable(stageFile)) {
                    located.setStatus(StatusType.NEW);
                } else {
                    located.setStatus(StatusType.INVALID);
                }
                
                this.mediaDao.saveEntity(located);
                artwork.getArtworkLocated().add(located);
            }
        }
        
        return true;
    }
    
    @Transactional
    public void processWatched(long id) {
        StageFile stageFile = stagingDao.getStageFile(id);
        LOG.info("Process watched {}-'{}'", stageFile.getId(), stageFile.getFileName());

        String videoBaseName = FilenameUtils.getBaseName(stageFile.getBaseName());
        String videoExtension = FilenameUtils.getExtension(stageFile.getBaseName());
        if (StringUtils.isNotBlank(videoBaseName) && StringUtils.isNotBlank(videoExtension)) {
            StageFile videoFile = stagingDao.getStageFile(FileType.VIDEO, videoBaseName, videoExtension, stageFile.getStageDirectory());
            this.commonStorageService.toogleWatchedStatus(videoFile, true, false);
        }
            
        // update stage file
        stageFile.setStatus(StatusType.DONE);
        stagingDao.updateEntity(stageFile);
    }
}
