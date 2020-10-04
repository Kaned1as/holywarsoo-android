package com.kanedias.holywarsoo.service

import android.content.Context
import android.util.Log
import ca.psiphon.PsiphonTunnel

/**
 * @author Kanedias
 *
 * Created on 04.10.20
 */
object CensorshipCircumvention: PsiphonTunnel.HostService {

    private lateinit var appCtx: Context
    private lateinit var psiphonTunnel: PsiphonTunnel

    var isConnected = false
    var socksProxyPort = -1

    fun init(ctx: Context) {
        appCtx = ctx

        psiphonTunnel = PsiphonTunnel.newPsiphonTunnel(this)
        psiphonTunnel.startTunneling("")
    }

    override fun getAppName() = appCtx.packageName
    override fun getContext() = appCtx

    override fun getPsiphonConfig() = """
        {
          "RemoteServerListUrl": "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed",
          "RemoteServerListSignaturePublicKey": "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM=",
          "SponsorId": "FFFFFFFFFFFFFFFF",
          "PropagationChannelId": "FFFFFFFFFFFFFFFF"
        }
        """

    override fun onListeningSocksProxyPort(port: Int) {
        socksProxyPort = port
    }

    override fun onDiagnosticMessage(message: String?) {
        Log.d("Psiphon", message ?: "")
    }

    override fun onConnecting() {
        Network.setupClient(proxyPort = null)
        isConnected = false
    }

    override fun onConnected() {
        isConnected = true
        Network.setupClient(proxyPort = socksProxyPort)
    }
}