package com.twindigital.costrisk.client;

import com.twindigital.costrisk.client.AwsCostExplorerClient.AwsCostData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsCostExplorerClientTest {

    @Mock
    private CostExplorerClient costExplorerClient;

    private AwsCostExplorerClient awsCostExplorerClient;

    @BeforeEach
    void setUp() {
        awsCostExplorerClient = new AwsCostExplorerClient("ap-south-1", 60L, costExplorerClient);
    }

    @Test
    @DisplayName("Should cache AWS Cost Explorer calls within TTL to prevent redundant billed requests")
    void shouldCacheCostExplorerCalls() {
        MetricValue metricValue = MetricValue.builder()
                .amount("120.50")
                .unit("USD")
                .build();

        Group group = Group.builder()
                .keys("Amazon Elastic Compute Cloud - Compute")
                .metrics(Map.of("UnblendedCost", metricValue))
                .build();

        ResultByTime resultByTime = ResultByTime.builder()
                .groups(List.of(group))
                .build();

        GetCostAndUsageResponse response = (GetCostAndUsageResponse) GetCostAndUsageResponse.builder()
                .resultsByTime(List.of(resultByTime))
                .build();

        when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(response);

        // First call - should call AWS SDK
        Optional<AwsCostData> firstCall = awsCostExplorerClient.getMonthlyCost("twindigital-cluster");
        assertThat(firstCall).isPresent();
        assertThat(firstCall.get().getTotalMonthlyCostUsd()).isEqualTo(120.50);

        // Second call - should return cached result without hitting AWS SDK again
        Optional<AwsCostData> secondCall = awsCostExplorerClient.getMonthlyCost("twindigital-cluster");
        assertThat(secondCall).isPresent();
        assertThat(secondCall.get().getTotalMonthlyCostUsd()).isEqualTo(120.50);

        // Verify AWS API was called EXACTLY ONCE
        verify(costExplorerClient, times(1)).getCostAndUsage(any(GetCostAndUsageRequest.class));
    }

    @Test
    @DisplayName("Should handle AWS Cost Explorer failures gracefully without throwing exceptions")
    void shouldHandleCostExplorerFailuresGracefully() {
        when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class)))
                .thenThrow(SdkClientException.create("AWS credentials not found or network error"));

        Optional<AwsCostData> result = awsCostExplorerClient.getMonthlyCost("twindigital-cluster");

        // Should return empty optional rather than throwing uncaught exception
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should filter Cost Explorer queries by cluster tag to scope to cluster resources rather than account-wide")
    void shouldFilterQueriesByClusterTag() {
        MetricValue metricValue = MetricValue.builder().amount("80.00").unit("USD").build();
        Group group = Group.builder().keys("Amazon Elastic Compute Cloud - Compute").metrics(Map.of("UnblendedCost", metricValue)).build();
        ResultByTime resultByTime = ResultByTime.builder().groups(List.of(group)).build();
        GetCostAndUsageResponse response = (GetCostAndUsageResponse) GetCostAndUsageResponse.builder()
                .resultsByTime(List.of(resultByTime))
                .build();

        org.mockito.ArgumentCaptor<GetCostAndUsageRequest> captor = org.mockito.ArgumentCaptor.forClass(GetCostAndUsageRequest.class);
        when(costExplorerClient.getCostAndUsage(captor.capture())).thenReturn(response);

        awsCostExplorerClient.getMonthlyCost("test-eks-cluster");

        GetCostAndUsageRequest captured = captor.getValue();
        assertThat(captured.filter()).isNotNull();
        assertThat(captured.filter().tags()).isNotNull();
        assertThat(captured.filter().tags().key()).isEqualTo("kubernetes.io/cluster/test-eks-cluster");
        assertThat(captured.filter().tags().values()).contains("owned", "shared");
    }
}
