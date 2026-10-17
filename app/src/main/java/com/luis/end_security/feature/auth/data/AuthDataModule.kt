package com.luis.end_security.feature.auth.data

import com.luis.end_security.feature.auth.domain.OnboardingRepository
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    abstract fun bindPasswordStrengthEstimator(impl: ZxcvbnPasswordStrengthEstimator): PasswordStrengthEstimator
}
