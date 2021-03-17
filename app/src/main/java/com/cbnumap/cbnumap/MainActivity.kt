package com.cbnumap.cbnumap

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.animation.TranslateAnimation
import androidx.core.app.ActivityCompat
import com.cbnumap.cbnumap.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private var coarseLocation = 1001
    private var fineLocation = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the SupportMapFragment and request notification when the map is ready to be used.
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        val screenHeight = getHeight(applicationContext)



        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //권한 요청하는 부분
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                fineLocation
            )
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                coarseLocation
            )
            return
        }

        var cameDown = false
        binding.comeDownBtn.setOnClickListener {
            val downSize = (screenHeight.toFloat() / 7f)
            val layout = binding.targetLinear
            val params = layout.layoutParams
            params.height = downSize.toInt() // layout height를 내려올 만큼으로 바꿈
            Log.e("downSize", downSize.toString())
            if (!cameDown) {
                binding.targetLinear.animate().translationY(downSize).withLayer().duration = 500
                binding.comeDownBtn.text = "GO UP"
                cameDown = true
            } else {
                binding.targetLinear.animate().translationY(-downSize).withLayer().duration = 500
                binding.comeDownBtn.text = "COME DOWN"
                cameDown = false
            }
        }

        //36.62758962182894, 127.45306974793739
        //명덕관에서 나오면 있는 곳 정도
        binding.searchBtn.setOnClickListener {
            val markerOption = MarkerOptions()
            val polyLine = mMap.addPolyline(
                PolylineOptions().clickable(true).add(
                    LatLng(36.62728942920238, 127.452753486),
                    LatLng(36.62758962182894, 127.45306974793739)
                )
            )
            markerOption.position(LatLng(36.62728942920238, 127.452753486)).title("0317")
            mMap.addMarker(markerOption)
        }

    }

    //36.62761494377374, 127.45305214428042
    private fun getHeight(context: Context): Int {
        var height: Int = 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayMetrics = DisplayMetrics()
            val display = context.display
            display!!.getRealMetrics(displayMetrics)
            displayMetrics.heightPixels
        } else {
            val displayMetrics = DisplayMetrics()
            this.windowManager.defaultDisplay.getMetrics(displayMetrics)
            height = displayMetrics.heightPixels
            height
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        var curLocLat = 36.62901804982402
        var curLocLng = 127.45631864038594

        mMap = googleMap // 변수 googleMap 초기화

        //권한 요청하는 부분
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                fineLocation
            )

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                coarseLocation
            )
            return
        }

        GlobalScope.launch {
            delay(100L)
            runOnUiThread {
                val btlSeongJae = LatLng(36.627628419782376, 127.4528052358374)
                googleMap?.apply {
                    Log.e("cur", curLocLat.toString())
                    moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(curLocLat, curLocLng),
                            16.0f
                        )
                    )
                }
            }
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Log.e("?", location?.latitude.toString())
                curLocLat = location?.latitude ?: 0.0
                curLocLng = location?.longitude ?: 0.0
                Log.e("Current Location", "${curLocLat}, $curLocLng")
            }
    }
}