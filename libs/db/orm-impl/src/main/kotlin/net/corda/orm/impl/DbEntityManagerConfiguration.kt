package net.corda.orm.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import javax.sql.DataSource

@Suppress("LongParameterList")
open class DbEntityManagerConfiguration(
    driverClass: String,
    jdbcUrl: String,
    username: String,
    password: String,
    override val ddlManage: DdlManage,
    override val showSql: Boolean,
    override val formatSql: Boolean,
    isAutoCommit: Boolean,
    maximumPoolSize: Int,
    hikariDataSourceFactory: (c: HikariConfig) -> HikariDataSource = { c ->
        HikariDataSource(c)
    }
) : EntityManagerConfiguration {
    private val ds by lazy {
        val conf = HikariConfig()
        conf.driverClassName = driverClass
        conf.jdbcUrl = jdbcUrl
        conf.username = username
        conf.password = password
        conf.isAutoCommit = isAutoCommit
        conf.maximumPoolSize = maximumPoolSize
        hikariDataSourceFactory(conf)
    }

    override val dataSource: DataSource
        get() = ds
}