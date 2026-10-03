package com.parksync.backend.repository;

import com.parksync.backend.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("""
        select u from AppUser u
        where :query is null or lower(u.name) like lower(concat('%', :query, '%'))
             or lower(u.email) like lower(concat('%', :query, '%'))
             or lower(u.phone) like lower(concat('%', :query, '%'))
        order by u.createdAt desc
        """)
    List<AppUser> search(@Param("query") String query);
}