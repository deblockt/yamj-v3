package com.moviejukebox.core.database.service;

import java.util.HashSet;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.moviejukebox.core.database.dao.CommonDao;
import com.moviejukebox.core.database.dao.MovieDao;
import com.moviejukebox.core.database.model.BoxedSet;
import com.moviejukebox.core.database.model.Certification;
import com.moviejukebox.core.database.model.Genre;
import com.moviejukebox.core.database.model.Movie;
import com.moviejukebox.core.database.model.SetDescriptor;

@Service("movieService")
public class MovieService {

    @Resource(name = "movieDao")
    private MovieDao movieDao;

    @Resource(name = "commonDao")
    private CommonDao commonDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void storeMovie(Movie movie) {
        
        // store genres
        HashSet<Genre> genres = new HashSet<Genre>(0);
        for (Genre genre : movie.getGenres()) {
            Genre stored = commonDao.getGenre(genre.getName());
            if (stored != null) {
                genres.add(stored);
            } else {
                commonDao.saveEntity(genre);
                genres.add(genre);
            }
        }
        movie.setGenres(genres);
        
        // store certification
        if (movie.getCertification() != null) {
            Certification stored = commonDao.getCertification(movie.getCertification().getName());
            if (stored != null) {
                movie.setCertification(stored);
            } else {
                commonDao.storeEntity(movie.getCertification());
            }
        }

        // store set descriptors
        for (BoxedSet boxedSet : movie.getBoxedSets()) {
            SetDescriptor setDescriptor = commonDao.getSetDescriptor(boxedSet.getSetDescriptor().getName());
            if (setDescriptor != null) {
                boxedSet.setSetDescriptor(setDescriptor);
            } else {
                commonDao.storeEntity(boxedSet.getSetDescriptor());
            }
        }

        // store the movie in the database
        movieDao.storeEntity(movie);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteMovie(Movie movie) {
        // delete the movie from the database
        movieDao.deleteEntity(movie);
    }
}