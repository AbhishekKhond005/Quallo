Name: quallo
Version: %{quallo_version}
Release: 1
Summary: Offline game production planner
License: Unspecified
URL: https://github.com/AbhishekKhond005/Quallo
BuildArch: x86_64
AutoReqProv: no
Requires: glibc, fontconfig, freetype, libX11, libXext, libXi, libXrender, libXtst, alsa-lib
%global __os_install_post %{nil}
%global _build_id_links none

%description
Quallo is a local desktop planner for game development with boards, checklists,
calendars, timelines and a project guide.

%install
install -d "%{buildroot}/opt/quallo"
cp -a "%{quallo_image}/." "%{buildroot}/opt/quallo/"
install -Dm644 "%{quallo_desktop}" "%{buildroot}%{_datadir}/applications/quallo.desktop"

%files
/opt/quallo
%{_datadir}/applications/quallo.desktop
