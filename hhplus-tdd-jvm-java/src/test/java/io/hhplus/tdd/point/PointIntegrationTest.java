package io.hhplus.tdd.point;

import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PointIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private PointService pointService;

    private static final String LOCAL_HOST = "http://localhost:";
    private static final String PATH = "/point";

    @Test
    @DisplayName("동시에 들어오는 다수의 충전 및 사용 요청을 처리한다")
    void chargeAndUseIfRequestAtTheSameTime() throws ExecutionException, InterruptedException {
        long userId = 1L; // 테스트를 위한 사용자 ID 가정
        long initialAmount = 10000L; // 초기 충전 금액
        long chargeAmount = 1000L; // 충전 금액
        long useAmount = 500L; // 사용 금액
        int numberOfTasks = 100; // 충전과 사용을 각각 수행할 작업 수

        // 초기 포인트 충전
        pointService.charge(userId, initialAmount);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 포인트 충전 작업
        for (int i = 0; i < numberOfTasks; i++) {
            CompletableFuture<Void> futureCharge = CompletableFuture.runAsync(() -> {
                try {
                    pointService.charge(userId, chargeAmount);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            futures.add(futureCharge);
        }

        // 포인트 사용 작업
        for (int i = 0; i < numberOfTasks; i++) {
            CompletableFuture<Void> futureUse = CompletableFuture.runAsync(() -> {
                try {
                    pointService.use(userId, useAmount);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            futures.add(futureUse);
        }

        // 모든 작업이 완료될 때까지 기다림
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.get();

        // 최종 포인트 잔액 검증
        UserPoint finalUserPoint = pointService.findPoint(userId);
        Long expectedFinalAmount = initialAmount + (chargeAmount * numberOfTasks) - (useAmount * numberOfTasks);
        assertThat(finalUserPoint.point()).isEqualTo(expectedFinalAmount);
    }

    @Test
    @DisplayName("특정 유저의 포인트를 조회한다")
    void getPointWhenUserExist() {
        // given
        long id = 1L;

        // when
        ResponseEntity<UserPoint> response = restTemplate.getForEntity(LOCAL_HOST + port + PATH + "/" + id, UserPoint.class);

        // then
        assertThat(Objects.requireNonNull(response.getBody()).id()).isEqualTo(1L);
        assertThat(response.getBody().point()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("포인트 이용 내역을 조회한다")
    void getPointHistory() {
        // given
        long id = 1L;

        // when
        ResponseEntity<List<PointHistory>> response = restTemplate.exchange(
                LOCAL_HOST + port + PATH + "/" + id + "/histories",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        // then
        assertThat(Objects.requireNonNull(response.getBody()).size()).isEqualTo(2);
        assertThat(response.getBody().get(0).amount()).isEqualTo(1000L);
    }
}
