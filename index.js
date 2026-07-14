import {AppRegistry} from 'react-native';
import App from './App';

/**
 * 入口组件名须与 RemoteRnActivity.getMainComponentName() 一致。
 * 根组件加载 Expo + @phonecode/remote-ui embedded 辅助逻辑。
 */
AppRegistry.registerComponent('PhoneCodeRemote', () => App);
