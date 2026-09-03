package com.example.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AmaratiApiService {

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @GET("api/v1/apartments")
    suspend fun getApartments(
        @Header("Authorization") token: String
    ): Response<List<ApartmentDto>>

    @GET("api/v1/projects")
    suspend fun getProjects(
        @Header("Authorization") token: String
    ): Response<List<ProjectDto>>

    @POST("api/v1/projects")
    suspend fun createProject(
        @Header("Authorization") token: String,
        @Body request: CreateProjectRequest
    ): Response<ProjectDto>

    @POST("api/v1/projects/{id}/approve")
    suspend fun approveProject(
        @Header("Authorization") token: String,
        @Path("id") projectId: String
    ): Response<ProjectDto>

    @POST("api/v1/ledger/payments")
    suspend fun recordPayment(
        @Header("Authorization") token: String,
        @Body request: PaymentRequest
    ): Response<LedgerDto>

    @GET("api/v1/ledger")
    suspend fun getLedger(
        @Header("Authorization") token: String
    ): Response<LedgerResponse>

    @POST("api/v1/sync/push")
    suspend fun pushSyncBatch(
        @Header("Authorization") token: String,
        @Body request: SyncPushRequest
    ): Response<SyncPushResponse>
}
