export const getDeviceId = (): string => {
  let deviceId = localStorage.getItem('csj_deviceId');
  if (!deviceId) {
    deviceId = 'csj_device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    localStorage.setItem('csj_deviceId', deviceId);
  }
  return deviceId;
};

export const getPackageName = (): string => {
  return 'com.jianxuqingdan.app';
};
