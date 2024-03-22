package org.jetlinks.community.device.configuration;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Juzipi
 * @version 1.0
 * @date 2024-03-21 9:26
 */
@Configuration
public class Client {
    @Value("${spring.elasticsearch.uris}")
    private String esUrl;

    @Bean
    RestHighLevelClient configRestHighLevelClient() throws Exception {

        String[] esUrlArr = esUrl.split(",");

        List<HttpHost> httpHosts = new ArrayList<>();
        for (String es : esUrlArr) {
            String[] esUrlPort = es.split(":");
            httpHosts.add(new HttpHost(esUrlPort[0], Integer.parseInt(esUrlPort[1]), "http"));
        }
        return new RestHighLevelClient(
            RestClient.builder(httpHosts.toArray(new HttpHost[0]))
        );
    }

}
