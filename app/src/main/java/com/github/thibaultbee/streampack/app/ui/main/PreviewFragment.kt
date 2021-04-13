/*
 * Copyright (C) 2021 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.thibaultbee.streampack.app.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.thibaultbee.streampack.app.databinding.MainFragmentBinding
import com.github.thibaultbee.streampack.app.utils.DialogUtils
import com.github.thibaultbee.streampack.app.utils.PreviewUtils.Companion.chooseBigEnoughSize
import com.github.thibaultbee.streampack.utils.getOutputSizes
import com.jakewharton.rxbinding4.view.clicks
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit


class PreviewFragment : Fragment() {
    private val fragmentDisposables = CompositeDisposable()
    private lateinit var binding: MainFragmentBinding

    private val viewModel: PreviewViewModel by lazy {
        ViewModelProvider(this).get(PreviewViewModel::class.java)
    }

    private val rxPermissions: RxPermissions by lazy { RxPermissions(this) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MainFragmentBinding.inflate(inflater, container, false)
        bindProperties()
        return binding.root
    }

    @SuppressLint("MissingPermission")
    private fun bindProperties() {
        binding.liveButton.clicks()
            .observeOn(AndroidSchedulers.mainThread())
            .compose(
                rxPermissions.ensureEachCombined(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
            .subscribe { permission ->
                if (!permission.granted) {
                    binding.liveButton.isChecked = false
                    showPermissionError()
                } else {
                    if (binding.liveButton.isChecked) {
                        viewModel.startStream()
                    } else {
                        viewModel.stopStream()
                    }
                }
            }
            .let(fragmentDisposables::add)

        binding.switchButton.clicks()
            .observeOn(AndroidSchedulers.mainThread())
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .compose(rxPermissions.ensure(Manifest.permission.CAMERA))
            .subscribe { granted ->
                if (!granted) {
                    showPermissionError()
                } else {
                    viewModel.toggleVideoSource()
                }
            }
            .let(fragmentDisposables::add)

        viewModel.error.observe(viewLifecycleOwner) {
            showError("Oops", it)
        }
    }

    private fun showPermissionError() {
        binding.liveButton.isChecked = false
        DialogUtils.showPermissionAlertDialog(requireContext())
    }

    private fun showError(title: String, message: String) {
        binding.liveButton.isChecked = false
        DialogUtils.showAlertDialog(requireContext(), "Error: $title", message)
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        rxPermissions
            .requestEachCombined(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .subscribe { permission ->
                if (!permission.granted) {
                    showPermissionError()
                } else {
                    viewModel.buildStreamer()
                }
            }

        binding.surfaceView.holder.addCallback(surfaceViewCallback)
    }

    override fun onStop() {
        super.onStop()
        binding.surfaceView.holder.setFixedSize(
            0,
            0
        ) // Ensure to trigger surface holder callback on resume
    }


    override fun onDestroy() {
        super.onDestroy()
        fragmentDisposables.clear()
    }

    @SuppressLint("MissingPermission")
    private val surfaceViewCallback = object : SurfaceHolder.Callback {
        var nbOnSurfaceChange = 0

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            require(context != null)

            rxPermissions
                .requestEachCombined(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                .subscribe { permission ->
                    if (!permission.granted) {
                        showPermissionError()
                    } else {
                        holder?.let {
                            nbOnSurfaceChange++
                            if (nbOnSurfaceChange == 2) {
                                viewModel.startCapture(holder.surface)
                            } else {
                                val choices = context!!.getOutputSizes(
                                    SurfaceHolder::class.java,
                                    viewModel.cameraId
                                )

                                chooseBigEnoughSize(choices, width, height)?.let { size ->
                                    holder.setFixedSize(size.width, size.height)
                                }
                            }
                        }
                    }
                }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            viewModel.stopCapture()
            binding.surfaceView.holder.removeCallback(this)
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
            nbOnSurfaceChange = 0
        }
    }
}