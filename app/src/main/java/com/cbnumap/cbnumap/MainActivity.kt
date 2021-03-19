package com.cbnumap.cbnumap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.ActivityCompat
import com.cbnumap.cbnumap.databinding.ActivityMainBinding
import com.cbnumap.cbnumap.server.Coordinate
import com.cbnumap.cbnumap.server.CoordinateDB
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var polyLine: Polyline

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private var coarseLocation = 1001
    private var fineLocation = 1002

    //room db 관련된 부분
    private var coordinateDB: CoordinateDB? = null
    private var coordinateList = listOf<Coordinate>()

    //빠른 길 찾기 부분
    data class Temp(val startId: Int, val endId: Int, val weight: Int) : Comparable<Temp> {
        override fun compareTo(other: Temp): Int = weight - other.weight
    }

    //빠른 길 찾기 부분
    private var pointList = Array(99999) { Stack<Temp>() }
    private val pq = PriorityQueue<Temp>() // 다익스트라 알고리즘 위한 priority queue
    private val dist = IntArray(99999) { Integer.MAX_VALUE - 1 } // 최댓값으로 구성
    private val from = IntArray(99999) { -1 } // 이전 점(위치)를 저장하기 위한 배열
    private val finalPath = mutableListOf<Int>()


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

        //RoomDB에서 데이터를 가져오는 부분
        coordinateDB = CoordinateDB.getInstance(this)
        val r = Runnable {
            // 빈 list로 초기화된 coordinateList에 Room db에 저장된 정보를 모두 읽어와서
            // Coordinate의 형태로 저장한다. getAll() 메서드를 통해 가져온다.
            // SubThread를 이용해서 Main Thread에 영향을 주지 않도록 해야 한다.
            coordinateList = coordinateDB?.coordinateDao()?.getAll()!!
            Log.e("kor_name", coordinateList[0].kor_name.toString())
            Log.e("latitude", coordinateList[0].latitude.toString())
            Log.e("longitude", coordinateList[0].longitude.toString())
            Log.e("id", coordinateList[0].id.toString())
        }
        val thread = Thread(r)
        thread.start()
        thread.join()


        //선을 잇는 부분 예시
        var searchBtnClicked = false

        binding.searchBtn.setOnClickListener {
            if (!searchBtnClicked) {
                drawOnMap()

                searchBtnClicked = true
                binding.searchBtn.text = "Remove Line"
            } else {
                polyLine.remove()
                mMap.clear()

                searchBtnClicked = false
                binding.searchBtn.text = "Search"
            }
        }

        addWeight()
        findPath()
    }

    private fun drawOnMap() {
        val markerOption = MarkerOptions()
        //소웨 과동이랑 소프트웨어 앞 사거리 연결해보기
        val latStart = coordinateList[1].latitude
        val latEnd = coordinateList[2].latitude
        val lngStart = coordinateList[1].longitude
        val lngEnd = coordinateList[2].longitude

        polyLine = mMap.addPolyline(
            PolylineOptions().clickable(true).add(
                LatLng(latStart, lngStart),
                LatLng(latEnd, lngEnd),
            )
        )
        markerOption.position(LatLng(latStart, lngStart)).title("출발점")
        mMap.addMarker(markerOption)
        markerOption.position(LatLng(latEnd, lngEnd)).title("도착점")
        mMap.addMarker(markerOption)
    }


    //TODO 추후에 여기에서 파라미터로 시작점, 끝 점을 받던가 해야할 것 같음.
    private fun findPath() {
        Log.d("find Path", "find Path In")
        //임시적으로 양성재에서 출발해서 소웨과동까지 가는 것 구현해봄
        pq.add(Temp(4, 7, 53)) // 출발점 pq에 입력
        dist[4] = 0 // 출발점 초기화
        while (pq.isNotEmpty()) {
//            println("$$$ $pq")
//            for (i in 0 until 10) {
//                print("${dist[i]} ")
//            }
//            println()
//            for (i in 0 until 10) {
//                print("${from[i]} ")
//            }
//            println()
            val point = pq.poll()!! // pq가 비어있지 않을 때 들어왔기 때문에, pq가 null이 될 수는 없다
            val startId = point.startId
            //val endId = point.endId
            //val weight = point.weight
            for (i in 0 until pointList[startId].size) { // 연결된 값들 pq에 추가
                // 기존에 있던 값(dist[pointList[startId][i].endId])이
                // 현재 위치한 노드의 dist(dist[pointList[startId][i].startId])에
                // 연결된 노드의 가중치(pointList[startId][i].weight)의 합이 더 작으면 초기화한다.
                // 쉽게 말해서 새로 탐색하려는 dist가 더 작으면 그 작은 값으로 해당 Point를 초기화 시켜주는 것.
                // 그리고 pq에 추가까지.
                //println("@@@ ${dist[pointList[startId][i].endId]}, ${dist[startId]}, ${pointList[startId][i].weight}")
//                Log.e(
//                    "@@@",
//                    "${pointList[startId][i].endId}, ${dist[pointList[startId][i].endId]}, ${dist[startId]}, ${pointList[startId][i].weight}"
//                )
                if (dist[pointList[startId][i].endId] > dist[startId] + pointList[startId][i].weight) {
                    dist[pointList[startId][i].endId] =
                        dist[pointList[startId][i].startId] + pointList[startId][i].weight
                    from[pointList[startId][i].endId] = pointList[startId][i].startId
                    // 업데이트 된 노드의 연결된 노드를 다음부터 탐색하기 위해 추가한다.
                    //Log.e("check", pointList[startId][i].endId.toString())
                    //Log.e("@@@", "${pointList[startId][i].endId}, ${dist[pointList[startId][i].endId]}, ${dist[startId]}, ${pointList[startId][i].weight}")
                    for (j in pointList[pointList[startId][i].endId]) {
                        //Log.e("check", "${j.startId}, ${j.endId}, ${j.weight}")
                        pq.add(Temp(j.startId, j.endId, j.weight))
                    }
                }
            }

        }

        var temp = 2 // 목적지로부터 역추적, TODO 추후에 여기 파라미터로 받아서 위치 넣어줘야함
        finalPath.add(2)
        while (true) {
            temp = from[temp]
            finalPath.add(temp)
            if (temp == 4) { // 출발지면 탈출, TODO 추후에 여기 파라미터로 받아서 위치 넣어줘야함
                break
            }
        }
        println("최종 경로 : $finalPath")
        Log.e("weight sum", dist[2].toString())
        //TODO 이제 그림 그려줘야 함

    }

    private fun drawLines() {
        val markerOption = MarkerOptions()
        //소웨 과동이랑 소프트웨어 앞 사거리 연결해보기
        val latStartPoint = coordinateList[3].latitude
        val latEndPoint = coordinateList[1].latitude
        val lngStartPoint = coordinateList[3].longitude
        val lngEndPoint = coordinateList[1].longitude

        for (i in 0 until finalPath.size - 1) {
            val cur = finalPath[i]-1 // 1씩 빼줘야 하는 이유는 coordinatelist에서는 0부터 값이 들어가있기 때문.
            val next = finalPath[i + 1]-1
            // 여기서와 마커를 찍을 때, 즉 coordinateList에서만 찍으면 되므로 그냥 1을 여기서 빼주는 식으로 처리
            val latStart = coordinateList[cur].latitude
            val latEnd = coordinateList[next].latitude
            val lngStart = coordinateList[cur].longitude
            val lngEnd = coordinateList[next].longitude

            polyLine = mMap.addPolyline(
                PolylineOptions().clickable(true).add(
                    LatLng(latStart, lngStart),
                    LatLng(latEnd, lngEnd),
                )
            )

        }

//        polyLine = mMap.addPolyline(
//            PolylineOptions().clickable(true).add(
//                LatLng(latStartPoint, lngStartPoint),
//                LatLng(latEndPoint, lngEndPoint),
//            )
//        )
        markerOption.position(LatLng(latStartPoint, lngStartPoint)).title("출발점")
        mMap.addMarker(markerOption)
        markerOption.position(LatLng(latEndPoint, lngEndPoint)).title("도착점")
        mMap.addMarker(markerOption)
    }


    private fun addWeight() {
        Log.d("addWeight", "addWeight In")
        pointList[1].add(Temp(1, 3, 79))
        pointList[1].add(Temp(1, 6, 134))
        pointList[2].add(Temp(2, 3, 48))
        pointList[3].add(Temp(3, 1, 79))
        pointList[3].add(Temp(3, 2, 48))
        pointList[3].add(Temp(3, 9, 80))
        pointList[4].add(Temp(4, 7, 53))
        pointList[5].add(Temp(5, 6, 43))
        pointList[5].add(Temp(5, 7, 41))
        pointList[5].add(Temp(5, 8, 48))
        pointList[6].add(Temp(6, 1, 134))
        pointList[6].add(Temp(6, 5, 43))
        pointList[7].add(Temp(7, 4, 53))
        pointList[7].add(Temp(7, 5, 41))
        pointList[8].add(Temp(8, 5, 48))
        pointList[8].add(Temp(8, 9, 80))
        pointList[9].add(Temp(9, 3, 80))
        pointList[9].add(Temp(9, 8, 80))
    }

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
                googleMap.apply {
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

        drawLines()
    }
}