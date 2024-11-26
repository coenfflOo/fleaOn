package com.ssafy.fleaOn.web.repository;

import com.ssafy.fleaOn.web.domain.RegionInfo;
import com.ssafy.fleaOn.web.dto.EupmyeonNameResponse;
import com.ssafy.fleaOn.web.dto.GugunNameResponse;
import com.ssafy.fleaOn.web.dto.SidoNameResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionInfoRepository extends JpaRepository<RegionInfo, Integer> {
    Optional<RegionInfo> findBySidoAndGugunAndEupmyeonAndLi(String sido, String gugun, String eupmyeon, String li);

    @Query("SELECT DISTINCT new com.ssafy.fleaOn.web.dto.SidoNameResponse(r.sido) FROM RegionInfo r")
    List<SidoNameResponse> findDistinctSido();

    @Query("SELECT DISTINCT new com.ssafy.fleaOn.web.dto.GugunNameResponse(r.gugun) FROM RegionInfo r WHERE r.sido = :sidoName")
    List<GugunNameResponse> findDistinctGugunBySido(@Param("sidoName") String sidoName);

    @Query("SELECT DISTINCT new com.ssafy.fleaOn.web.dto.EupmyeonNameResponse(r.eupmyeon, r.regionCode) FROM RegionInfo r WHERE r.sido = :sidoName AND r.gugun = :gugunName")
    List<EupmyeonNameResponse> findDistinctBySidoAndGugun(@Param("sidoName") String sidoName, @Param("gugunName") String gugunName);

    Optional<RegionInfo> findByRegionCode(String regionCode);
}
