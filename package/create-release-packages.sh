#!/usr/bin/env sh
#
# NOTE:
# This file's intended use is to build release packages of the Swing wallet
#
# It includes pre-consenting to the Java runtime's ToS as a step to downloading the runtime
# for Windows packaging.
#
# On renaming "LICENSE" to "LICENSE.txt" -- the reasoning was such that our users are
# choosing to use a GUI interface, and as such a text-content file would more than likely
# be preferred with the ".txt" extension for potentially easier opening in a GUI text
# editor.
#
SCRIPT=`realpath $0`
SCRIPT_PATH=`dirname "${SCRIPT}"`
PROJECT_ROOT="${SCRIPT_PATH}/.."
cd "${PROJECT_ROOT}"

# Download and verify binaries for the Hush daemon & command line utility
build_output_base_path="build"
build_output_jars_path="${build_output_base_path}/jars"
download_directory_path="package/dl"
temporary_directory_path="package/tmp"
release_directory_path="package/release-ready"

cleanup() {
    cd "${PROJECT_ROOT}"
    rm -rf "${build_output_base_path}"
    rm -rf "${download_directory_path}"
    rm -rf "${temporary_directory_path}"
}

cleanup_and_err() {
    echo "Error: ${1}"
    cleanup
    exit 1
}

cleanup
rm -rf "${release_directory_path}"

# Build the .jar file
./build.sh

# Download binary distributions of the Hush daemon & utilities
mkdir "${download_directory_path}"
cd "${download_directory_path}"

hush_binary_release_version="v1.0.13"

download_and_verify() {
    releases_path=$1
    release_version=$2
    binary_package=$3
    wget "${releases_path}/download/${release_version}/${binary_package}"
    wget "${releases_path}/download/${release_version}/${binary_package}.sha256"
    sha256sum -c "${binary_package}.sha256"

    if [ "$?" != 0 ]; then
        cleanup_and_err "Unable to verify SHA256 checksum for ${binary_package}";
    fi
}

download_and_verify_hush_package() {
    binary_package=$1
    base_hush_releases_path="https://github.com/MyHush/hush/releases"
    download_and_verify "${base_hush_releases_path}" "${hush_binary_release_version}" "${binary_package}"
}

# Download and verify binaries for all platforms
windows_binaries_package="bin-windows-x64.zip"
download_and_verify_hush_package "${windows_binaries_package}"

# TODO: download and verify Mac binaries (not available yet)

linux_binaries_package="bin-linux-x64.tar.gz"
download_and_verify_hush_package "${linux_binaries_package}"

#* Now we have verified binaries and a built .jar file
cd "${PROJECT_ROOT}"
mkdir "${temporary_directory_path}"
mkdir "${release_directory_path}"

jar_release_verison="v0.71.2b"
jar_file_name="HUSHSwingWalletUI.jar"

## Prepare Windows non-bundled zip release
windows_release_package_name="hush-swing-${jar_release_verison}-hush-${hush_binary_release_version}--no-runtime--windows-x64"
mkdir "${temporary_directory_path}/${windows_release_package_name}"
unzip "${download_directory_path}/${windows_binaries_package}" -d "${temporary_directory_path}/${windows_release_package_name}"
cp "package/supplemental-windows/reindex.bat" "${temporary_directory_path}/${windows_release_package_name}/"
cp "package/supplemental-windows/run.bat" "${temporary_directory_path}/${windows_release_package_name}/"
cp "package/supplemental-windows/NON-RT-USAGE.txt" "${temporary_directory_path}/${windows_release_package_name}/USAGE.txt"
cp "${build_output_jars_path}/${jar_file_name}" "${temporary_directory_path}/${windows_release_package_name}/"
cp LICENSE "${temporary_directory_path}/${windows_release_package_name}/LICENSE.txt"

cd "${temporary_directory_path}/${windows_release_package_name}"
zip -9r "${PROJECT_ROOT}/${release_directory_path}/${windows_release_package_name}.zip" *
cd "${PROJECT_ROOT}"
sha256sum "${release_directory_path}/${windows_release_package_name}.zip" > "${release_directory_path}/${windows_release_package_name}.zip.sha256"


## Prepare Windows runtime-bundled zip release
windows_full_release_package_name="hush-swing-${jar_release_verison}-hush-${hush_binary_release_version}-windows-x64"
mkdir "${temporary_directory_path}/${windows_full_release_package_name}"
mkdir "${temporary_directory_path}/${windows_full_release_package_name}/app"

# Move our MSVC dependencies and `hush.exe` Java runtime loader, with it's `hush.cfg` file from their included archive
# TODO: Eventually, avoid having this binary in this repository or as a hard-dependency at all
unzip "package/supplemental-windows/hush-jruntime-bootstrapper-windows-x64.zip" -d "${temporary_directory_path}/${windows_full_release_package_name}/"

unzip "${download_directory_path}/${windows_binaries_package}" -d "${temporary_directory_path}/${windows_full_release_package_name}/app/"
cp "package/supplemental-windows/reindex.bat" "${temporary_directory_path}/${windows_full_release_package_name}/app/"
cp "${build_output_jars_path}/${jar_file_name}" "${temporary_directory_path}/${windows_full_release_package_name}/app/"
cp LICENSE "${temporary_directory_path}/${windows_full_release_package_name}/LICENSE.txt"

# Download Java SE JRE to be bundled
java_runtime_binary_package_name="server-jre-8u162-windows-x64"
wget --header "Cookie: oraclelicense=a" \
 "http://download.oracle.com/otn-pub/java/jdk/8u162-b12/0da788060d494f5095bf8624735fa2f1/${java_runtime_binary_package_name}.tar.gz" \
 -O "${download_directory_path}/${java_runtime_binary_package_name}.tar.gz"
cp "package/supplemental-windows/${java_runtime_binary_package_name}.tar.gz.sha256" "${download_directory_path}/"
cd "${download_directory_path}"
sha256sum -c "${java_runtime_binary_package_name}.tar.gz.sha256"
if [ "$?" != 0 ]; then
    cleanup_and_err "Unable to verify SHA256 checksum for ${binary_package}";
fi
cd "${PROJECT_ROOT}"

# Move only the 'runtime' folder from the JRE distribution to be included in our release
mkdir "${temporary_directory_path}/${java_runtime_binary_package_name}"
tar -xzvf "${download_directory_path}/${java_runtime_binary_package_name}.tar.gz" -C "${temporary_directory_path}/${java_runtime_binary_package_name}"
cp -r "${temporary_directory_path}/${java_runtime_binary_package_name}/jdk1.8.0_162/jre" "${temporary_directory_path}/${windows_full_release_package_name}/runtime"

# Make release file
cd "${temporary_directory_path}/${windows_full_release_package_name}"
zip -9r "${PROJECT_ROOT}/${release_directory_path}/${windows_full_release_package_name}.zip" *
cd "${PROJECT_ROOT}"
sha256sum "${release_directory_path}/${windows_full_release_package_name}.zip" > "${release_directory_path}/${windows_full_release_package_name}.zip.sha256"


## Prepare Linux non-bundled zip release
linux_release_package_name="hush-swing-${jar_release_verison}-hush-${hush_binary_release_version}-linux-x64"
mkdir "${temporary_directory_path}/${linux_release_package_name}"
tar -xzvf "${download_directory_path}/${linux_binaries_package}" -C "${temporary_directory_path}/${linux_release_package_name}"
cp "package/supplemental-shell/run.sh" "${temporary_directory_path}/${linux_release_package_name}/"
cp "package/supplemental-shell/USAGE.txt" "${temporary_directory_path}/${linux_release_package_name}/USAGE.txt"
cp "${build_output_jars_path}/${jar_file_name}" "${temporary_directory_path}/${linux_release_package_name}/"
cp LICENSE "${temporary_directory_path}/${linux_release_package_name}/LICENSE.txt"

cd "${temporary_directory_path}/${linux_release_package_name}"
tar -czvf "${PROJECT_ROOT}/${release_directory_path}/${linux_release_package_name}.tar.gz" *
cd "${PROJECT_ROOT}"
sha256sum "${release_directory_path}/${linux_release_package_name}.tar.gz" > "${release_directory_path}/${linux_release_package_name}.tar.gz.sha256"

# TODO: Add readme to each archive (customized per platform)

# All done
cleanup
