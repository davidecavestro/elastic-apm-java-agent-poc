package com.davidecavestro.elastic.apm.client.retrofit;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface RetrofitApmApiService {
//  @GET("users/{user}/repos")
//  Call<List<Repo>> sendTransactions(@Path("user") String user);

  @POST("/v1/transactions")
  Call<Void> sendTransactions(@Body com.davidecavestro.elastic.apm.client.model.transactions.ApmPayload transactions);

  @POST("/v1/errors")
  Call<Void> sendErrors(@Body com.davidecavestro.elastic.apm.client.model.errors.ApmPayload errors);
}
