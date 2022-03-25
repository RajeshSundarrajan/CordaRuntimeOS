import './App.css';

import { CssBaseline, ThemeProvider } from '@material-ui/core';
import { Route, Routes } from 'react-router-dom';

import { AppDataContextProvider } from 'contexts/AppDataContext';
import Configuration from 'pages/configuration/Configuration';
import ConnectFourWrapper from 'pages/connectfour/ConnectFourWrapper';
import Footer from 'components/footer/Footer';
import NavButtons from 'components/navbar/navbuttons/NavButtons';
import { BrowserRouter as Router } from 'react-router-dom';
import { SnackbarProvider } from 'notistack';
import TicTacToeWrapper from 'pages/ticTacToe/TicTacToeWrapper';
import TopNavWrapper from 'components/navbar/TopNavWrapper';
import VideogameAssetIcon from '@material-ui/icons/VideogameAsset';
import theme from 'constants/theme';

function App() {
    return (
        <SnackbarProvider
            maxSnack={3}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'right',
            }}
        >
            <ThemeProvider theme={theme}>
                <CssBaseline />
                <Router>
                    <TopNavWrapper
                        navButtons={<NavButtons />}
                        icon={<VideogameAssetIcon color="secondary" style={{ width: 50, height: 50 }} />}
                    />
                    <div style={{ marginBottom: 120 }}>
                        <AppDataContextProvider>
                            <Routes>
                                <Route path="/" element={<Configuration />} />
                                <Route path="/tic-tac-toe" element={<TicTacToeWrapper />} />
                                <Route path="/connect4" element={<ConnectFourWrapper />} />
                            </Routes>
                        </AppDataContextProvider>
                    </div>
                    <Footer copyright={'© 2022 R3. All rights reserved.'} />
                </Router>
            </ThemeProvider>
        </SnackbarProvider>
    );
}

export default App;