package org.jetlinks.community.device.service.data;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import org.checkerframework.checker.units.qual.C;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.community.device.entity.SeriesData;
import org.jetlinks.community.device.response.ChartDataResponse;
import org.jetlinks.community.device.service.ContentEsRepository;
import org.jetlinks.community.device.web.request.data.QueryDataRequest;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.things.data.AggregationRequest;
import org.jetlinks.community.things.data.operations.ColumnModeQueryOperations;
import org.jetlinks.community.things.data.operations.SaveOperations;
import org.jetlinks.core.device.DeviceThingType;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.metadata.DeviceMetadata;
import org.jetlinks.community.device.entity.DeviceEvent;
import org.jetlinks.community.device.entity.DeviceOperationLogEntity;
import org.jetlinks.community.device.entity.DeviceProperty;
import org.jetlinks.community.things.ThingsDataRepository;
import org.jetlinks.community.things.data.PropertyAggregation;
import org.jetlinks.community.timeseries.query.AggregationData;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@AllArgsConstructor
public class ThingsBridgingDeviceDataService implements DeviceDataService {
    private final ThingsDataRepository repository;

    //引入es
    private final ElasticSearchService elasticSearchService;

    static final String thingType = DeviceThingType.device.getId();

    @Override
    public Mono<Void> registerMetadata(@Nonnull String productId, @Nonnull DeviceMetadata metadata) {

        return repository
            .opsForTemplate(thingType, productId)
            .flatMap(opt -> opt.forDDL().registerMetadata(metadata));
    }

    @Override
    public Mono<Void> reloadMetadata(@Nonnull String productId, @Nonnull DeviceMetadata metadata) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMap(opt -> opt.forDDL().reloadMetadata(metadata));
    }

    @Nonnull
    @Override
    public Mono<Void> saveDeviceMessage(@Nonnull DeviceMessage message) {
        return repository.opsForSave().save(message);
    }

    @Nonnull
    @Override
    public Mono<Void> saveDeviceMessage(@Nonnull Publisher<DeviceMessage> message) {
        return repository.opsForSave().save(message);
    }

    @Nonnull
    @Override
    public Flux<DeviceProperty> queryEachOneProperties(@Nonnull String deviceId, @Nonnull QueryParamEntity query, @Nonnull String... properties) {
        return queryEachProperties(deviceId, query.clone().doPaging(0, 1), properties);
    }

    @Nonnull
    @Override
    public Flux<DeviceProperty> queryEachProperties(@Nonnull String deviceId, @Nonnull QueryParamEntity query, @Nonnull String... properties) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMapMany(opt -> opt.forQuery().queryEachProperty(query, properties))
            .map(DeviceProperty::of);
    }

    @Nonnull
    @Override
    public Flux<DeviceProperty> queryProperty(@Nonnull String deviceId, @Nonnull QueryParamEntity query, @Nonnull String... property) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMapMany(opt -> opt.forQuery().queryProperty(query, property))
            .map(DeviceProperty::of);
    }

    @Nonnull
    public Flux<DeviceProperty> queryPropertyByProductId(@Nonnull String productId, @Nonnull QueryParamEntity query, @Nonnull String... property) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMapMany(opt -> opt.forQuery().queryProperty(query, property))
            .map(DeviceProperty::of);
    }

    @Nonnull
    public Flux<DeviceProperty> queryTopProperty(@Nonnull String deviceId,
                                                 @Nonnull AggregationRequest request,
                                                 int numberOfTop,
                                                 @Nonnull String... properties) {
        return Flux.error(new UnsupportedOperationException("unsupported"));
    }

    @Override
    public Flux<AggregationData> aggregationPropertiesByProduct(@Nonnull String productId,
                                                                @Nonnull AggregationRequest request,
                                                                @Nonnull DevicePropertyAggregation... properties) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMapMany(opt -> opt.forQuery()
                                   .aggregationProperties(
                                       FastBeanCopier.copy(request, new org.jetlinks.community.things.data.AggregationRequest()),
                                       Stream
                                           .of(properties)
                                           .map(prop -> FastBeanCopier.copy(prop, new PropertyAggregation()))
                                           .toArray(PropertyAggregation[]::new)));
    }

    @Override
    public Flux<AggregationData> aggregationPropertiesByDevice(@Nonnull String deviceId, @Nonnull AggregationRequest request, @Nonnull DevicePropertyAggregation... properties) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMapMany(opt -> opt
                .forQuery()
                .aggregationProperties(
                    FastBeanCopier.copy(request, new org.jetlinks.community.things.data.AggregationRequest()),
                    Stream
                        .of(properties)
                        .map(prop -> FastBeanCopier.copy(prop, new PropertyAggregation()))
                        .toArray(PropertyAggregation[]::new)));
    }

    @Nonnull
    @Override
    public Mono<PagerResult<DeviceProperty>> queryPropertyPage(@Nonnull String deviceId, @Nonnull String property, @Nonnull QueryParamEntity query) {
        QueryDataRequest queryDataRequest = new QueryDataRequest();
        return queryPropertyPage(deviceId, query, property);
    }

    @Nonnull
    public Mono<PagerResult<DeviceProperty>> queryPropertyPage(@Nonnull String deviceId, @Nonnull QueryParamEntity query, @Nonnull String... property) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMap(opt -> opt.forQuery().queryPropertyPage(query, property))
            .map(page -> convertPage(page, DeviceProperty::of));
    }

    private <R, T> PagerResult<R> convertPage(PagerResult<T> source, Function<T, R> mapper) {
        @SuppressWarnings("all")
        PagerResult<R> newResult = FastBeanCopier.copy(source, source.getClass());

        newResult.setData(
            source.getData()
                  .stream()
                  .map(mapper)
                  .collect(Collectors.toList())
        );
        return newResult;
    }

    @Nonnull
    public Mono<PagerResult<DeviceProperty>> queryPropertyPageByProductId(@Nonnull String productId, @Nonnull String property, @Nonnull QueryParamEntity query) {
        return queryPropertyPageByProductId(property, query, property);
    }

    @Nonnull
    public Mono<PagerResult<DeviceProperty>> queryPropertyPageByProductId(@Nonnull String productId, @Nonnull QueryParamEntity query, @Nonnull String... property) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMap(opt -> opt.forQuery().queryPropertyPage(query, property))
            .map(page -> convertPage(page, DeviceProperty::of));
    }

    @Override
    public Mono<PagerResult<DeviceOperationLogEntity>> queryDeviceMessageLog(@Nonnull String deviceId, @Nonnull QueryParamEntity query) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMap(opt -> opt.forQuery().queryMessageLogPage(query))
            .map(page -> convertPage(page, DeviceOperationLogEntity::of));
    }

    public Flux<DeviceOperationLogEntity> queryDeviceMessageLogNoPaging(@Nonnull String deviceId, @Nonnull QueryParamEntity query) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMapMany(opt -> opt.forQuery().queryMessageLog(query))
            .map(DeviceOperationLogEntity::of);
    }

    public Flux<DeviceOperationLogEntity> queryDeviceMessageLogNoPagingByProduct(@Nonnull String productId, @Nonnull QueryParamEntity query) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMapMany(opt -> opt.forQuery().queryMessageLog(query))
            .map(DeviceOperationLogEntity::of);
    }

    @Nonnull
    @Override
    public Flux<DeviceEvent> queryEvent(@Nonnull String deviceId, @Nonnull String event, @Nonnull QueryParamEntity query, boolean format) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMapMany(opt -> opt.forQuery().queryEvent(event, query, format))
            .map(DeviceEvent::new);
    }

    @Nonnull
    @Override
    public Mono<PagerResult<DeviceEvent>> queryEventPage(@Nonnull String deviceId, @Nonnull String event, @Nonnull QueryParamEntity query, boolean format) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMap(opt -> opt.forQuery().queryEventPage(event, query, format))
            .map(page -> convertPage(page, DeviceEvent::new));
    }

    @Nonnull
    public Mono<PagerResult<DeviceEvent>> queryEventPageByProductId(@Nonnull String productId,
                                                                    @Nonnull String event,
                                                                    @Nonnull QueryParamEntity query,
                                                                    boolean format) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMap(opt -> opt.forQuery().queryEventPage(event, query, format))
            .map(page -> convertPage(page, DeviceEvent::new));
    }


    @Nonnull
    @Override
    public Flux<DeviceProperties> queryProperties(@Nonnull String deviceId, @Nonnull QueryParamEntity query) {
        return repository
            .opsForThing(thingType, deviceId)
            .flatMapMany(opt -> opt.forQuery().unwrap(ColumnModeQueryOperations.class).queryAllProperties(query))
            .map(DeviceProperties::new);
    }

    @Nonnull
    @Override
    public Mono<PagerResult<DeviceProperties>> queryPropertiesPageByProduct(@Nonnull String productId, @Nonnull QueryParamEntity query) {
        return repository
            .opsForTemplate(thingType, productId)
            .flatMap(opt -> opt.forQuery().unwrap(ColumnModeQueryOperations.class).queryAllPropertiesPage(query))
            .map(page -> convertPage(page, DeviceProperties::new));
    }

    @Autowired
    private RestHighLevelClient client;

    @Nonnull
    @Override
    public Mono<PagerResult<ChartDataResponse>> queryHistryDataOwn(String productId, QueryDataRequest queryDataRequest) {

        Date date = new Date();
        SimpleDateFormat dateFormat= new SimpleDateFormat("yyyy-M");
        String time = dateFormat.format(date);

        String result = String.format("properties_%s_%s", productId, time);

        SearchRequest searchRequest = new SearchRequest(result);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        List<String> properties = queryDataRequest.getProperties();

        TermsQueryBuilder termsQueryBuilder = QueryBuilders.termsQuery("property", properties);
        TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery("deviceId", queryDataRequest.getDeviceId());

        boolQueryBuilder.filter(termsQueryBuilder);
        boolQueryBuilder.filter(termQueryBuilder);

        String queryRangTime = String.format("now-%sm/m", queryDataRequest.getRangeTime());
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("timestamp").gte(queryRangTime);
        boolQueryBuilder.filter(rangeQueryBuilder);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(boolQueryBuilder);
        //一分钟60条数据
        sourceBuilder.size(queryDataRequest.getCountSize());
        searchRequest.source(sourceBuilder);

        try {
            ChartDataResponse chartDataResponse = new ChartDataResponse();
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            SearchHit[] hitsList = hits.getHits();
            List<String> xList = new ArrayList<>();
            SeriesData seriesData = new SeriesData();
            List<String> dataList = new ArrayList<>();
            List<SeriesData> seriesDataList = new ArrayList<>();
            for (SearchHit documentFields : hitsList) {
                Map<String, Object> sourceAsMap = documentFields.getSourceAsMap();
                //从Map中获取 "value" 和 "timestamp" 字段
                String value = (String) sourceAsMap.get("value");
                long timestamp = (long) sourceAsMap.get("timestamp");

                //处理时间错
                date = new Date(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String formattedTime = sdf.format(date);
                xList.add(formattedTime);

                dataList.add(value);


            }
            seriesData.setData(dataList);
            seriesDataList.add(seriesData);
            chartDataResponse.setXAxisData(xList);
            chartDataResponse.setSeriesData(seriesDataList);
//            Gson gson = new Gson();
//            String jsonResp = gson.toJson(chartDataResponse);

            PagerResult<ChartDataResponse> pagerResult = new PagerResult<>();
            List<ChartDataResponse> arrayList = new ArrayList<>();
            arrayList.add(chartDataResponse);
            pagerResult.setData(arrayList);
            return Mono.just(pagerResult);
        } catch (IOException e) {
            System.out.println("执行错误了奥");
        }

        return null;
    }

//    private String dateConvert(String  date){
//
//        return formattedTime;
//    }

}
